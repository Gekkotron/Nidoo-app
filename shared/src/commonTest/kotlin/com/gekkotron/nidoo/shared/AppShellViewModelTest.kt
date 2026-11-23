package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.GatewayConfig
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.RoutePreference
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.transport.Route
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-shell-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private object XorSecrets : SecretStore {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(cipher: ByteArray) = encrypt(cipher)
    }

    private class Shell(val profiles: ProfileRepository, val preferences: AppPreferences, val session: EntitySourceSession, val vm: AppShellViewModel)

    private fun TestScope.shell(): Shell {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val profiles = ProfileRepository.create(dir.resolve(ProfileRepository.FILE), XorSecrets, backgroundScope)
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        val session = EntitySourceSession(FakeMqttTransport(), backgroundScope)
        return Shell(profiles, preferences, session, AppShellViewModel(profiles, preferences, session))
    }

    @Test
    fun setUpAgainForgetsTheProfileAndTheSource() = runTest {
        val s = shell()
        s.profiles.save(ConnectionProfile.Demo)
        s.profiles.state.first { it is ProfileState.Ready }
        s.session.current(ConnectionProfile.Demo)
        s.vm.setUpAgain()
        assertEquals(ProfileState.None, s.profiles.state.first { it !is ProfileState.Ready })
        // reset() follows clear(), which resumes from DataStore's IO thread: wait for it in real time.
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { while (s.session.peek() != null) delay(10) } }
        assertNull(s.session.peek())
    }

    @Test
    fun themeFollowsThePreference() = runTest {
        val s = shell()
        s.preferences.setTheme(ThemePreference.IVOIRE)
        assertEquals(ThemeChoice.IVOIRE, s.vm.theme.first { it == ThemeChoice.IVOIRE })
    }

    @Test
    fun finishNotificationsClearsTheStep() = runTest {
        val s = shell()
        s.profiles.save(ConnectionProfile.Direct(LocalBroker("broker.local")))
        assertIs<ProfileState.Ready>(s.profiles.state.first { it is ProfileState.Ready && it.notificationsPending })
        s.vm.finishNotifications()
        assertFalse((s.profiles.state.first { it is ProfileState.Ready && !it.notificationsPending } as ProfileState.Ready).notificationsPending)
    }

    @Test
    fun setRouteSavesThePreference() = runTest {
        val s = shell()
        s.profiles.save(ConnectionProfile.Direct(LocalBroker("broker.local"), gateway = GatewayConfig("https://gw.example", ByteArray(GatewayConfig.KEY_BYTES), "broker.local")))
        s.profiles.state.first { it is ProfileState.Ready }
        s.vm.setRoute(checkNotNull(s.vm.route.first { it != null }).let { Route.GATEWAY })
        advanceUntilIdle()
        assertEquals(RoutePreference.GATEWAY, s.profiles.route.first { it == RoutePreference.GATEWAY })
    }
}
