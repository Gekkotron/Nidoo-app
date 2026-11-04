package com.gekkotron.nidoo.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppPreferencesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * DataStore answers from its own IO thread, which races runTest's virtual time (a 60 s
     * UncompletedCoroutinesError). So: real time, a real scope cancelled at the end, a real timeout.
     */
    private fun realTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            withTimeout(10_000) { scope.block() }
        } finally {
            scope.cancel()
        }
    }

    private fun file(name: String = "prefs.json") = tmp.root.resolve(name)
    private fun CoroutineScope.prefs(f: File = file()) = AppPreferences.create(f, this)
    private suspend fun AppPreferences.loaded() = theme.filterNotNull().first()

    @Test
    fun `assist client id is created once and kept`() {
        var first = ""
        realTest {
            val p = prefs()
            first = p.assistClientId()
            assertTrue(first.startsWith("android-"), first)
            assertEquals(first, p.assistClientId())
        }
        // A new instance on the same file (the first one's scope is cancelled by realTest).
        realTest { assertEquals(first, prefs().assistClientId()) }
    }

    @Test
    fun `concurrent first assist client id calls agree`() = realTest {
        val p = prefs()
        val a = async { p.assistClientId() }
        val b = async { p.assistClientId() }
        assertEquals(a.await(), b.await())
    }

    @Test
    fun `missing file is System`() = realTest {
        assertEquals(ThemePreference.SYSTEM, prefs().loaded())
    }

    @Test
    fun `theme round-trips`() = realTest {
        val p = prefs()
        p.setTheme(ThemePreference.IVOIRE)
        assertEquals(ThemePreference.IVOIRE, p.theme.first { it == ThemePreference.IVOIRE })
    }

    @Test
    fun `a corrupt file falls back to System`() = realTest {
        val f = file().apply { writeText("{not json") }
        assertEquals(ThemePreference.SYSTEM, prefs(f).loaded())
    }

    @Test
    fun `an unknown theme name from a newer app is System`() = realTest {
        val f = file().apply { writeText("""{"theme":"OLED_BLACK"}""") }
        assertEquals(ThemePreference.SYSTEM, prefs(f).loaded())
    }

    @Test
    fun `clearing the profile keeps the theme`() = realTest {
        val p = prefs()
        p.setTheme(ThemePreference.NIGHT_GLASS)
        val profiles = ProfileRepository.create(file("profile.json"), FakeSecretStore(), this)
        profiles.save(ConnectionProfile.Demo)
        profiles.clear()
        assertEquals(ThemePreference.NIGHT_GLASS, p.theme.first { it == ThemePreference.NIGHT_GLASS })
    }

    @Test
    fun `voiceEnabled defaults to false`() = realTest {
        assertEquals(false, prefs().voiceEnabled.filterNotNull().first())
    }

    @Test
    fun `voiceEnabled round-trips`() = realTest {
        val p = prefs()
        p.setVoiceEnabled(true)
        assertEquals(true, p.voiceEnabled.first { it == true })
        p.setVoiceEnabled(false)
        assertEquals(false, p.voiceEnabled.first { it == false })
    }

    @Test
    fun `an older file without voiceEnabled stays false`() = realTest {
        val f = file().apply { writeText("""{"theme":"WARM_MINIMAL"}""") }
        val p = prefs(f)
        assertEquals(false, p.voiceEnabled.filterNotNull().first())
        assertEquals(ThemePreference.IVOIRE, p.theme.first { it == ThemePreference.IVOIRE })
    }

    @Test
    fun `energyViewDefaultsToClassic`() = realTest {
        assertEquals(EnergyViewPreference.CLASSIC, prefs().energyView.filterNotNull().first())
    }

    @Test
    fun `setEnergyViewPersists`() = realTest {
        val f = file()
        val p = prefs(f)
        p.setEnergyView(EnergyViewPreference.ORBITE)
        assertEquals(EnergyViewPreference.ORBITE, p.energyView.first { it == EnergyViewPreference.ORBITE })
        assertEquals(true, f.readText().contains("\"energyView\":\"ORBITE\""))
    }

    @Test
    fun `unknownEnergyViewDecodesAsClassic`() = realTest {
        val f = file().apply { writeText("""{"energyView":"SPIRAL"}""") }
        assertEquals(EnergyViewPreference.CLASSIC, prefs(f).energyView.filterNotNull().first())
    }

    @Test
    fun `a stored WARM_MINIMAL opens as Ivoire`() = realTest {
        val f = file().apply { writeText("""{"theme":"WARM_MINIMAL"}""") }
        assertEquals(ThemePreference.IVOIRE, prefs(f).theme.first { it != null })
    }

    @Test
    fun `presence defaults to disabled`() = realTest {
        val p = prefs().presence.filterNotNull().first()
        assertEquals(false, p.enabled)
        assertEquals("", p.alias)
        assertEquals(null, p.homeLat)
        assertEquals(null, p.homeLng)
        assertEquals(150, p.radiusM)
    }

    @Test
    fun `presence round-trips`() = realTest {
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
    fun `an older file without presence keeps theme and defaults presence to disabled`() = realTest {
        val f = file().apply { writeText("""{"theme":"NIGHT_GLASS","voiceEnabled":true}""") }
        val p = prefs(f)
        assertEquals(false, p.presence.filterNotNull().first().enabled)
        assertEquals(ThemePreference.NIGHT_GLASS, p.theme.first { it == ThemePreference.NIGHT_GLASS })
        assertEquals(true, p.voiceEnabled.first { it == true })
    }

    @Test
    fun `presence radius is clamped to 50 to 500`() = realTest {
        val p = prefs()
        p.setPresence(PresenceConfig(enabled = true, alias = "a", homeLat = 0.0, homeLng = 0.0, radiusM = 10))
        assertEquals(50, p.presence.first { it != null && it.radiusM == 50 }!!.radiusM)
        p.setPresence(PresenceConfig(enabled = true, alias = "a", homeLat = 0.0, homeLng = 0.0, radiusM = 5000))
        assertEquals(500, p.presence.first { it != null && it.radiusM == 500 }!!.radiusM)
    }

    @Test
    fun `missing file selects House`() = realTest {
        assertEquals(DashboardChoice("house", null), prefs().selectedDashboard.filterNotNull().first())
    }

    @Test
    fun `dashboard choice round-trips`() = realTest {
        val p = prefs()
        p.setSelectedDashboard("evening", "Soirée")
        assertEquals(DashboardChoice("evening", "Soirée"), p.selectedDashboard.first { it == DashboardChoice("evening", "Soirée") })
    }

    @Test
    fun `an old file without the field selects House`() = realTest {
        val f = file().apply { writeText("""{"theme":"IVOIRE"}""") }
        assertEquals(DashboardChoice("house", null), prefs(f).selectedDashboard.filterNotNull().first())
    }

    @Test
    fun `replaceSelectedDashboard only writes while the stored id is the expected one`() = realTest {
        val p = AppPreferences.create(file("replace.json"), this)
        p.setSelectedDashboard("b", "B")
        p.replaceSelectedDashboard("a", "house", null) // stale: the user has moved on to b
        assertEquals(DashboardChoice("b", "B"), p.selectedDashboard.first { it == DashboardChoice("b", "B") })
        p.replaceSelectedDashboard("b", "b", "Bee")
        assertEquals(DashboardChoice("b", "Bee"), p.selectedDashboard.first { it == DashboardChoice("b", "Bee") })
    }

    @Test
    fun `clearing the profile keeps the dashboard choice`() = realTest {
        val p = prefs()
        p.setSelectedDashboard("evening", "Soirée")
        val profiles = ProfileRepository.create(file("profile.json"), FakeSecretStore(), this)
        profiles.save(ConnectionProfile.Demo)
        profiles.clear()
        assertEquals(DashboardChoice("evening", "Soirée"), p.selectedDashboard.first { it == DashboardChoice("evening", "Soirée") })
    }

    private fun store(serializer: Serializer<StoredPreferences>, scope: CoroutineScope, f: File = file()) =
        DataStoreFactory.create(serializer, ReplaceFileCorruptionHandler { StoredPreferences() }, scope = scope) { f }

    /** Holds every write between DataStore's version bump and the file rename, so a read can be staged inside that window. */
    private class HeldWrites : Serializer<StoredPreferences> by PreferencesSerializer {
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        override suspend fun writeTo(t: StoredPreferences, output: OutputStream) {
            writing.complete(Unit)
            release.await()
            PreferencesSerializer.writeTo(t, output)
        }
    }

    /**
     * Regression: DataStore's `data` starts with an unlocked read that, while a write is between its
     * version bump and its rename, returns the old file tagged with the new version, then drops the
     * write itself as "already seen". A write overlapping the first read must still reach the flows.
     */
    @Test
    fun `a write overlapping the first read is not lost`() = runBlocking {
        val prefsThread = Executors.newSingleThreadExecutor()
        val firstReadMayStart = CountDownLatch(1)
        prefsThread.execute { firstReadMayStart.await() } // queues the preferences' own work behind this
        val scope = CoroutineScope(prefsThread.asCoroutineDispatcher() + SupervisorJob())
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val held = HeldWrites()
        try {
            withTimeout(10_000) {
                val p = AppPreferences(store(held, storeScope), scope)
                val write = async(Dispatchers.Default) { p.setTheme(ThemePreference.IVOIRE) }
                held.writing.await() // the version is bumped; the new file is not in place yet
                firstReadMayStart.countDown()
                // Let a first read run inside the window if the implementation starts one there.
                withTimeoutOrNull(500) { p.theme.first { it != null } }
                held.release.countDown()
                write.await()
                val seen = withTimeoutOrNull(2_000) { p.theme.first { it == ThemePreference.IVOIRE } }
                assertEquals(ThemePreference.IVOIRE, seen, "the write was lost: theme stayed ${p.theme.value}")
            }
        } finally {
            held.release.countDown()
            firstReadMayStart.countDown()
            scope.cancel()
            storeScope.cancel()
            prefsThread.shutdown()
        }
    }

    /** The unstaged shape of the same race: a write straight after creation, many times over. */
    @Test
    fun `a write straight after creation is always seen`() = runBlocking {
        var lost = 0
        repeat(STRESS_ROUNDS) { i ->
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            try {
                val p = AppPreferences.create(file("stress-$i.json"), scope)
                val want = DashboardChoice("d$i", "D $i")
                p.setSelectedDashboard(want.id, want.name)
                if (withTimeoutOrNull(1_000) { p.selectedDashboard.first { it == want } } == null) lost++
            } finally {
                scope.cancel()
            }
        }
        assertEquals(0, lost, "$lost of $STRESS_ROUNDS writes never reached selectedDashboard")
    }

    @Test
    fun `a write shows as soon as the setter returns`() = realTest {
        val p = prefs()
        p.setSelectedDashboard("a", "A")
        assertEquals(DashboardChoice("a", "A"), p.selectedDashboard.value)
        p.setTheme(ThemePreference.IVOIRE)
        assertEquals(ThemePreference.IVOIRE, p.theme.value)
        p.setVoiceEnabled(true)
        assertEquals(true, p.voiceEnabled.value)
        p.setPresence(PresenceConfig(enabled = true, alias = "x", radiusM = 100))
        assertEquals(PresenceConfig(enabled = true, alias = "x", radiusM = 100), p.presence.value)
    }

    @Test
    fun `concurrent writers leave the flows on what the file holds`() = realTest {
        val f = file("concurrent.json")
        val p = prefs(f)
        coroutineScope {
            repeat(50) { i -> launch(Dispatchers.Default) { p.setSelectedDashboard("d$i", "D $i") } }
            repeat(50) { i -> launch(Dispatchers.Default) { p.setVoiceEnabled(i % 2 == 0) } }
            repeat(30) { i -> launch(Dispatchers.Default) { p.setTheme(ThemePreference.entries[i % ThemePreference.entries.size]) } }
        }
        val onDisk = f.inputStream().use { PreferencesSerializer.readFrom(it) }
        assertEquals(DashboardChoice(onDisk.dashboardId, onDisk.dashboardName), p.selectedDashboard.value)
        assertEquals(onDisk.voiceEnabled, p.voiceEnabled.value)
        assertEquals(onDisk.theme, p.theme.value?.name)
    }

    @Test
    fun `a write whose caller is cancelled still shows`() = realTest {
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val held = HeldWrites()
        try {
            val p = AppPreferences(store(held, storeScope), this)
            p.theme.filterNotNull().first()
            val caller = launch { p.setTheme(ThemePreference.IVOIRE) }
            held.writing.await()
            caller.cancel() // DataStore still finishes the write
            held.release.countDown()
            caller.join()
            assertEquals(ThemePreference.IVOIRE, p.theme.value)
        } finally {
            held.release.countDown()
            storeScope.cancel()
        }
    }

    private companion object {
        const val STRESS_ROUNDS = 300
    }
}
