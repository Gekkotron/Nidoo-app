package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.create
import com.gekkotron.nidoo.feature.settings.FakePushStatusSource
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.push.PushStatus
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
class AndroidSettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    // DataStore runs on its own scope, not on the test's backgroundScope: its cancellation during @After
    // would otherwise hit Dispatchers.Main.immediate after resetMain and crash on CI runners.
    private val storage = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

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

    private fun kotlinx.coroutines.test.TestScope.vm(prefs: AppPreferences, push: FakePushStatusSource = FakePushStatusSource()): AndroidSettingsViewModel {
        val vm = AndroidSettingsViewModel(push, prefs, SpeechAvailability.Status.Ready)
        backgroundScope.launch { vm.voice.collect {} }
        return vm
    }

    @Test
    fun `voice is off by default`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs.json"), storage)
        val vm = vm(prefs)
        assertEquals(false, vm.voice.value.enabled)
    }

    @Test
    fun `setVoiceEnabled persists the preference`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs.json"), storage)
        val vm = vm(prefs)
        vm.setVoiceEnabled(true)
        assertEquals(true, prefs.voiceEnabled.first { it == true })
        assertTrue(vm.voice.first { it.enabled }.enabled)
    }

    @Test
    fun `the push status is carried and followed`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs2.json"), storage)
        val push = FakePushStatusSource(PushStatus.Waiting)
        val vm = vm(prefs, push)
        assertEquals(PushStatus.Waiting, vm.push.value)
        push.status.value = PushStatus.NotificationsOff
        assertEquals(PushStatus.NotificationsOff, vm.push.first { it == PushStatus.NotificationsOff })
    }

    @Test
    fun `register again and refresh go to the push source`() = runTest(dispatcher) {
        val prefs = AppPreferences.create(tmp.root.resolve("prefs3.json"), storage)
        val push = FakePushStatusSource()
        val vm = vm(prefs, push)
        vm.registerAgain()
        vm.refreshPush()
        assertEquals(1, push.registerAgains)
        assertEquals(1, push.refreshes)
    }
}
