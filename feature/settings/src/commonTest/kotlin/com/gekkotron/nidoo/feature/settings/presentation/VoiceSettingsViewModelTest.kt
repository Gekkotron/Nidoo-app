package com.gekkotron.nidoo.feature.settings.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.feature.voice.VoicePermissions
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

class FakeVoicePermissions(var grant: Boolean) : VoicePermissions {
    var requests = 0
        private set

    override suspend fun request(): Boolean {
        requests++
        return grant
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSettingsViewModelTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-voice-settings-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun voiceTest(
        permissions: FakeVoicePermissions,
        availability: SpeechAvailability.Status = SpeechAvailability.Status.Ready,
        before: suspend (AppPreferences) -> Unit = {},
        body: suspend TestScope.(AppPreferences, VoiceSettingsViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        preferences.voiceEnabled.first { it != null }
        before(preferences)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory {
            initializer { VoiceSettingsViewModel(preferences, availability, permissions) }
        })[VoiceSettingsViewModel::class]
        try {
            body(preferences, vm)
        } finally {
            store.clear()
        }
    }

    @Test
    fun grantedPermissionTurnsVoiceOn() = voiceTest(FakeVoicePermissions(true)) { preferences, vm ->
        vm.setVoiceEnabled(true)
        assertEquals(true, preferences.voiceEnabled.first { it == true })
        assertEquals(false, vm.denied.value)
    }

    @Test
    fun refusedPermissionKeepsVoiceOff() = voiceTest(FakeVoicePermissions(false)) { preferences, vm ->
        vm.setVoiceEnabled(true)
        assertEquals(true, vm.denied.first { it })
        assertEquals(false, preferences.voiceEnabled.value)
        vm.deniedShown()
        assertEquals(false, vm.denied.value)
    }

    @Test
    fun turningOffAsksNothing() {
        val permissions = FakeVoicePermissions(true)
        voiceTest(permissions) { preferences, vm ->
            vm.setVoiceEnabled(false)
            assertEquals(0, permissions.requests)
            assertEquals(false, preferences.voiceEnabled.first { it != null })
        }
    }

    @Test
    fun notReadyShowsTheToggleOff() = voiceTest(
        FakeVoicePermissions(true),
        availability = SpeechAvailability.Status.NoOnDevice,
        before = { it.setVoiceEnabled(true) },
    ) { _, vm ->
        assertEquals(VoiceSettingsUi(enabled = true, availability = SpeechAvailability.Status.NoOnDevice), vm.voice.value)
    }
}
