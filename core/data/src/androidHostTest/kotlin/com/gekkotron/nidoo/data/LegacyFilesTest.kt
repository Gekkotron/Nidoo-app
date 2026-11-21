package com.gekkotron.nidoo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Files written by the app before :core:data went multiplatform (captured with the java.io DataStore
 * serializers). The new code must read them, and write them back byte for byte.
 */
class LegacyFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun legacy(name: String): File {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/legacy/$name")) { "missing legacy/$name" }.use { it.readBytes() }
        return tmp.root.resolve(name).apply { writeBytes(bytes) }
    }

    private val gatewayKey = ByteArray(32) { it.toByte() }
    private val profile = ConnectionProfile.Direct(
        LocalBroker("192.168.1.91", 8883, tls = true, ws = false, username = "nidoo", password = "p@ss wörd 🔑"),
        prefix = "homeassistant",
        gateway = GatewayConfig("https://gw.example/athena", gatewayKey, "192.168.1.91"),
        pushTokenTopic = "fcm-push/tokens/{installId}",
        historyUrl = "http://192.168.1.91:8123",
        frigate = FrigateConfig("http://frigate:8971", "admin", "fr1gate"),
    )
    private val messages = linkedMapOf(
        "homeassistant/light/salon/config" to """{"name":"Salon","stat_t":"~/state","~":"nidoo/salon"}""",
        "nidoo/salon/state" to "ON",
        "homeassistant/sensor/temp/state" to "21.5 °C",
    )

    @Test
    fun readsLegacyPreferences() = runTest {
        val p = AppPreferences.create(legacy("preferences"), backgroundScope)
        assertEquals(ThemePreference.IVOIRE, p.theme.first { it != null })
        assertEquals(true, p.voiceEnabled.value)
        assertEquals(PresenceConfig(true, "Pixel de Léa", 48.8566, 2.3522, 220), p.presence.value)
        assertEquals(DashboardChoice("dash-7", "Étage"), p.selectedDashboard.value)
        assertEquals(EnergyViewPreference.ORBITE, p.energyView.value)
        assertEquals("android-a674cc62-65cf-4840-aff1-cc65197787c2", p.assistClientId())
    }

    @Test
    fun readsLegacyProfilesAndSecrets() = runTest {
        val r = ProfileRepository.create(legacy("profiles"), FakeSecretStore(), backgroundScope)
        val ready = r.state.first { it is ProfileState.Ready } as ProfileState.Ready
        assertEquals(ProfileState.Ready(profile, roomsPending = false, notificationsPending = true), ready)
        assertContentEquals(gatewayKey, (ready.profile as ConnectionProfile.Direct).gateway?.key)
        assertEquals(RoutePreference.GATEWAY, r.route.first { it == RoutePreference.GATEWAY })
        assertEquals("41f25e0a-400d-48b5-aa91-6c5e9a45508f", r.installId())
    }

    @Test
    fun readsLegacyEntityCache() = runTest {
        val c = FileEntityCache.create(legacy("entity-cache"), backgroundScope)
        assertEquals(messages, c.load("profile-key-1"))
        assertEquals(emptyMap(), c.load("another-profile"))
    }

    /** DataStore skips a write that changes nothing, so each store makes a change and reverts it. */
    @Test
    fun rewrittenFileIsByteIdentical() = runTest {
        val prefsFile = legacy("preferences")
        val prefs = AppPreferences.create(prefsFile, backgroundScope)
        prefs.setTheme(ThemePreference.SYSTEM)
        prefs.setTheme(ThemePreference.IVOIRE)

        val profileFile = legacy("profiles")
        val repo = ProfileRepository.create(profileFile, FakeSecretStore(), backgroundScope)
        repo.setRoute(RoutePreference.AUTO)
        repo.setRoute(RoutePreference.GATEWAY)

        val cacheFile = legacy("entity-cache")
        val cache = FileEntityCache.create(cacheFile, backgroundScope)
        val loaded = cache.load("profile-key-1")
        cache.clear()
        cache.save("profile-key-1", loaded)

        for ((name, file) in listOf("preferences" to prefsFile, "profiles" to profileFile, "entity-cache" to cacheFile)) {
            val original = checkNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }
            assertEquals(original.decodeToString(), file.readBytes().decodeToString(), name)
        }
    }
}
