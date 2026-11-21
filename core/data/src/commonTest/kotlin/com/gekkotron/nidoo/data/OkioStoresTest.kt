package com.gekkotron.nidoo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three stores on okio paths, on every platform: what one instance writes, the next one reads from disk. */
class OkioStoresTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-data-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(dir)

    /** Runs [block] on a scope that is cancelled afterwards, so the next store may open the same file. */
    private suspend fun TestScope.session(block: suspend (CoroutineScope) -> Unit) {
        val job = Job(backgroundScope.coroutineContext[Job])
        block(CoroutineScope(backgroundScope.coroutineContext + job))
        job.cancelAndJoin()
    }

    @Test
    fun preferencesSurviveANewInstance() = runTest {
        val path = dir.resolve(AppPreferences.FILE)
        session { AppPreferences.create(path, it).setTheme(ThemePreference.IVOIRE) }
        session { assertEquals(ThemePreference.IVOIRE, AppPreferences.create(path, it).theme.first { t -> t != null }) }
    }

    @Test
    fun profileAndSecretsSurviveANewInstance() = runTest {
        val path = dir.resolve(ProfileRepository.FILE)
        val secrets = FakeSecretStore()
        val profile = ConnectionProfile.Direct(
            LocalBroker("broker.local", username = "u", password = "p@ss wörd 🔑"),
            gateway = GatewayConfig("https://gw.example", ByteArray(GatewayConfig.KEY_BYTES) { it.toByte() }, null),
        )
        session { ProfileRepository.create(path, secrets, it).save(profile) }
        session {
            val state = ProfileRepository.create(path, secrets, it).state.first { s -> s is ProfileState.Ready }
            assertEquals(ProfileState.Ready(profile, roomsPending = true, notificationsPending = true), state)
        }
    }

    @Test
    fun entityCacheSurvivesANewInstance() = runTest {
        val path = dir.resolve(FileEntityCache.FILE)
        val messages = mapOf("nidoo/salon/state" to "ON", "homeassistant/sensor/t/state" to "21.5 °C")
        session { FileEntityCache.create(path, it).save("key", messages) }
        session { assertEquals(messages, FileEntityCache.create(path, it).load("key")) }
    }
}
