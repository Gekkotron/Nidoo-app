package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.feature.settings.FakeEntitySource
import com.gekkotron.nidoo.feature.settings.FakePushStatusSource
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsVoiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    // DataStore runs on its own scope, not on the test's backgroundScope: its cancellation during @After
    // would otherwise hit Dispatchers.Main.immediate after resetMain and crash on CI runners.
    private val storage = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    private object PassThrough : SecretStore {
        override fun encrypt(plain: ByteArray) = plain.copyOf()
        override fun decrypt(cipher: ByteArray) = cipher.copyOf()
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        // cancel() alone is non-blocking: DataStore writes that resume AFTER resetMain() lose both the
        // test dispatcher and (because Robolectric is tearing down) the Android Looper fallback, which
        // crashes with "Dispatchers.Main was accessed when the platform dispatcher was absent".
        runBlocking { storage.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.vm(source: FakeEntitySource, prefs: AppPreferences): SettingsViewModel {
        val profiles = ProfileRepository.create(tmp.root.resolve("profile.json"), PassThrough, storage)
        val vm = SettingsViewModel(profiles, prefs, FakePushStatusSource(), source, SpeechAvailability.Status.Ready)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test
    fun `voice is off by default`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs.json"), storage)
        val vm = vm(FakeEntitySource(), prefs)
        assertEquals(false, vm.state.value.voice.enabled)
    }

    @Test
    fun `setVoiceEnabled persists the preference`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs.json"), storage)
        val vm = vm(FakeEntitySource(), prefs)
        vm.setVoiceEnabled(true)
        assertEquals(true, prefs.voiceEnabled.first { it == true })
        assertTrue(vm.state.first { it.voice.enabled }.voice.enabled)
    }
}
