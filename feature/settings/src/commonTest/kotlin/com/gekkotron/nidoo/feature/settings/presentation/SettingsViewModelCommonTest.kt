package com.gekkotron.nidoo.feature.settings.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The common half of Settings' ViewModel, run on every target (the Robolectric suite keeps the rest). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelCommonTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-settings-${Random.nextLong().toULong()}")
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

    private class Fixture(val profiles: ProfileRepository, val preferences: AppPreferences, val store: ViewModelStore, val vm: SettingsViewModel)

    /**
     * The preferences and profile are real, on a temp dir; [before] runs ahead of the ViewModel. Clears the ViewModel
     * inside the test, before backgroundScope is cancelled and Main is reset.
     */
    private fun settingsTest(
        before: suspend (ProfileRepository, AppPreferences) -> Unit = { _, _ -> },
        body: suspend TestScope.(Fixture) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val profiles = ProfileRepository.create(dir.resolve(ProfileRepository.FILE), XorSecrets, backgroundScope)
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        before(profiles, preferences)
        val source = EntitySourceSession(FakeMqttTransport(), backgroundScope).current(null)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory {
            initializer { SettingsViewModel(profiles, preferences, source, NoPlatformSettings) }
        })[SettingsViewModel::class]
        try {
            body(Fixture(profiles, preferences, store, vm))
        } finally {
            store.clear()
        }
    }

    private suspend fun ProfileRepository.awaitHistoryUrl(url: String?) =
        state.first { ((it as? ProfileState.Ready)?.profile as? ConnectionProfile.Direct)?.historyUrl == url }

    @Test
    fun themeIsSeededFromTheStore() = settingsTest(before = { _, preferences ->
        preferences.setTheme(ThemePreference.IVOIRE)
        preferences.theme.first { it == ThemePreference.IVOIRE }
    }) { s ->
        // Nothing collects the state: the first frame already says Ivoire.
        assertEquals(ThemePreference.IVOIRE, s.vm.state.value.theme)
    }

    @Test
    fun noPlatformMeansNoPlatformRows() = settingsTest { s ->
        assertTrue(s.vm.state.value.platformRows.isEmpty())
    }

    @Test
    fun aBadHistoryUrlIsRefused() = settingsTest(before = { profiles, _ ->
        profiles.save(ConnectionProfile.Direct(LocalBroker("h"), historyUrl = "http://192.168.1.91:8081"))
        profiles.awaitHistoryUrl("http://192.168.1.91:8081")
    }) { s ->
        assertFalse(s.vm.setHistoryUrl("not a url"))
        // Refused before anything is written: the stored override is still there.
        assertEquals("http://192.168.1.91:8081", ((s.profiles.state.value as ProfileState.Ready).profile as ConnectionProfile.Direct).historyUrl)
    }

    @Test
    fun aBlankHistoryUrlClearsTheOverride() = settingsTest(before = { profiles, _ ->
        profiles.save(ConnectionProfile.Direct(LocalBroker("h"), historyUrl = "http://192.168.1.91:8081"))
        profiles.awaitHistoryUrl("http://192.168.1.91:8081")
    }) { s ->
        assertTrue(s.vm.setHistoryUrl("  "))
        s.profiles.awaitHistoryUrl(null)
    }
}
