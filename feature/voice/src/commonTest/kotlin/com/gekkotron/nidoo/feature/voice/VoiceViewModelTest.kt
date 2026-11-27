package com.gekkotron.nidoo.feature.voice

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gekkotron.nidoo.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-voice-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    /** Builds the ViewModel over real preferences and clears it inside the test, before Main is reset. */
    private fun voiceTest(speech: SpeechCaptureFactory, body: (VoiceViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory {
            initializer { VoiceViewModel(preferences, FakeAssistClient(), FakePlayer(), speech) }
        })[VoiceViewModel::class]
        try {
            body(vm)
        } finally {
            store.clear()
        }
    }

    @Test fun noRecognizerShowsSpeechError() = voiceTest(speech = { null }) { vm ->
        vm.start()
        assertEquals(VoiceSessionState.Error(ErrorKind.SPEECH), vm.state.value)
        assertFalse(vm.started)
    }

    @Test fun startUsesTheFactoryOnce() {
        var calls = 0
        val capture = FakeSpeechCapture()
        voiceTest(speech = { calls++; capture }) { vm ->
            vm.start()
            vm.start()
            assertEquals(1, calls)
            assertEquals(1, capture.starts)
            assertTrue(vm.started)
        }
    }

    @Test fun permissionDeniedShowsThePermissionError() = voiceTest(speech = { null }) { vm ->
        vm.showPermissionDenied()
        assertEquals(VoiceSessionState.Error(ErrorKind.PERMISSION), vm.state.value)
    }
}
