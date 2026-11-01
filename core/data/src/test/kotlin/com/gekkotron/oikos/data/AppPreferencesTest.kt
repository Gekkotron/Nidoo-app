package com.gekkotron.oikos.data

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class AppPreferencesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(name: String = "prefs.json") = tmp.root.resolve(name)
    private fun TestScope.prefs(f: File = file()) = AppPreferences.create(f, backgroundScope)
    private suspend fun AppPreferences.loaded() = theme.filterNotNull().first()

    @Test
    fun `missing file is System`() = runTest {
        assertEquals(ThemePreference.SYSTEM, prefs().loaded())
    }

    @Test
    fun `theme round-trips`() = runTest {
        val p = prefs()
        p.setTheme(ThemePreference.IVOIRE)
        assertEquals(ThemePreference.IVOIRE, p.theme.first { it == ThemePreference.IVOIRE })
    }

    @Test
    fun `a corrupt file falls back to System`() = runTest {
        val f = file().apply { writeText("{not json") }
        assertEquals(ThemePreference.SYSTEM, prefs(f).loaded())
    }

    @Test
    fun `an unknown theme name from a newer app is System`() = runTest {
        val f = file().apply { writeText("""{"theme":"OLED_BLACK"}""") }
        assertEquals(ThemePreference.SYSTEM, prefs(f).loaded())
    }

    @Test
    fun `clearing the profile keeps the theme`() = runTest {
        val p = prefs()
        p.setTheme(ThemePreference.NIGHT_GLASS)
        val profiles = ProfileRepository.create(file("profile.json"), FakeSecretStore(), backgroundScope)
        profiles.save(ConnectionProfile.Demo)
        profiles.clear()
        assertEquals(ThemePreference.NIGHT_GLASS, p.theme.first { it == ThemePreference.NIGHT_GLASS })
    }

    @Test
    fun `voiceEnabled defaults to false`() = runTest {
        assertEquals(false, prefs().voiceEnabled.filterNotNull().first())
    }

    @Test
    fun `voiceEnabled round-trips`() = runTest {
        val p = prefs()
        p.setVoiceEnabled(true)
        assertEquals(true, p.voiceEnabled.first { it == true })
        p.setVoiceEnabled(false)
        assertEquals(false, p.voiceEnabled.first { it == false })
    }

    @Test
    fun `an older file without voiceEnabled stays false`() = runTest {
        val f = file().apply { writeText("""{"theme":"WARM_MINIMAL"}""") }
        val p = prefs(f)
        assertEquals(false, p.voiceEnabled.filterNotNull().first())
        assertEquals(ThemePreference.IVOIRE, p.theme.first { it == ThemePreference.IVOIRE })
    }

    @Test
    fun `a stored WARM_MINIMAL opens as Ivoire`() = runTest {
        val f = file().apply { writeText("""{"theme":"WARM_MINIMAL"}""") }
        assertEquals(ThemePreference.IVOIRE, prefs(f).theme.first { it != null })
    }

    @Test
    fun `presence defaults to disabled`() = runTest {
        val p = prefs().presence.filterNotNull().first()
        assertEquals(false, p.enabled)
        assertEquals("", p.alias)
        assertEquals(null, p.homeLat)
        assertEquals(null, p.homeLng)
        assertEquals(150, p.radiusM)
    }

    @Test
    fun `presence round-trips`() = runTest {
        val p = prefs()
        p.setPresence(PresenceConfig(enabled = true, alias = "pixel", homeLat = 48.858, homeLng = 2.294, radiusM = 200))
        val back = p.presence.first { it != null && it.enabled }!!
        assertEquals(true, back.enabled)
        assertEquals("pixel", back.alias)
        assertEquals(48.858, back.homeLat)
        assertEquals(2.294, back.homeLng)
        assertEquals(200, back.radiusM)
    }

    @Test
    fun `an older file without presence keeps theme and defaults presence to disabled`() = runTest {
        val f = file().apply { writeText("""{"theme":"NIGHT_GLASS","voiceEnabled":true}""") }
        val p = prefs(f)
        assertEquals(false, p.presence.filterNotNull().first().enabled)
        assertEquals(ThemePreference.NIGHT_GLASS, p.theme.first { it == ThemePreference.NIGHT_GLASS })
        assertEquals(true, p.voiceEnabled.first { it == true })
    }

    @Test
    fun `presence radius is clamped to 50 to 500`() = runTest {
        val p = prefs()
        p.setPresence(PresenceConfig(enabled = true, alias = "a", homeLat = 0.0, homeLng = 0.0, radiusM = 10))
        assertEquals(50, p.presence.first { it != null && it.radiusM == 50 }!!.radiusM)
        p.setPresence(PresenceConfig(enabled = true, alias = "a", homeLat = 0.0, homeLng = 0.0, radiusM = 5000))
        assertEquals(500, p.presence.first { it != null && it.radiusM == 500 }!!.radiusM)
    }

    @Test
    fun `missing file selects House`() = runTest {
        assertEquals(DashboardChoice("house", null), prefs().selectedDashboard.filterNotNull().first())
    }

    @Test
    fun `dashboard choice round-trips`() = runTest {
        val p = prefs()
        p.setSelectedDashboard("evening", "Soirée")
        assertEquals(DashboardChoice("evening", "Soirée"), p.selectedDashboard.first { it == DashboardChoice("evening", "Soirée") })
    }

    @Test
    fun `an old file without the field selects House`() = runTest {
        val f = file().apply { writeText("""{"theme":"IVOIRE"}""") }
        assertEquals(DashboardChoice("house", null), prefs(f).selectedDashboard.filterNotNull().first())
    }

    @Test
    fun `replaceSelectedDashboard only writes while the stored id is the expected one`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined + kotlinx.coroutines.SupervisorJob())
        try {
            val p = AppPreferences.create(file("replace.json"), scope)
            p.setSelectedDashboard("b", "B")
            p.replaceSelectedDashboard("a", "house", null) // stale: the user has moved on to b
            assertEquals(DashboardChoice("b", "B"), p.selectedDashboard.first { it == DashboardChoice("b", "B") })
            p.replaceSelectedDashboard("b", "b", "Bee")
            assertEquals(DashboardChoice("b", "Bee"), p.selectedDashboard.first { it == DashboardChoice("b", "Bee") })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `clearing the profile keeps the dashboard choice`() = runTest {
        val p = prefs()
        p.setSelectedDashboard("evening", "Soirée")
        val profiles = ProfileRepository.create(file("profile.json"), FakeSecretStore(), backgroundScope)
        profiles.save(ConnectionProfile.Demo)
        profiles.clear()
        assertEquals(DashboardChoice("evening", "Soirée"), p.selectedDashboard.first { it == DashboardChoice("evening", "Soirée") })
    }
}
