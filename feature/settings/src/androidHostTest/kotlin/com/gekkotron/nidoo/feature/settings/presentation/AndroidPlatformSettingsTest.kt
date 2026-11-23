package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.PresenceConfig
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
import java.time.Instant
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPlatformSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    // DataStore runs on its own scope: see SettingsViewModelTest.
    private val storage = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        runBlocking { storage.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    private fun prefs() = AppPreferences.create(tmp.root.resolve("prefs.json"), storage)

    @Test
    fun rowsFollowPushPresenceAndVoice() = runTest(dispatcher) {
        val prefs = prefs()
        prefs.setPresence(PresenceConfig(enabled = true))
        prefs.setVoiceEnabled(true)
        prefs.presence.first { it?.enabled == true }
        prefs.voiceEnabled.first { it == true }
        val platform = AndroidPlatformSettings(FakePushStatusSource(PushStatus.Registered(Instant.parse("2026-11-02T10:00:00Z"))), prefs, SpeechAvailability.Status.Ready)
        val expected = listOf(
            PlatformRow(SettingsPage.NOTIFICATIONS, RowStatus.PUSH_REGISTERED),
            PlatformRow(SettingsPage.PRESENCE, RowStatus.ON),
            PlatformRow(SettingsPage.VOICE, RowStatus.ON),
        )
        assertEquals(expected, platform.rows.first())
        assertEquals(expected, platform.currentRows())
    }

    @Test
    fun everyPushStatusHasItsRowStatus() = runTest(dispatcher) {
        val push = FakePushStatusSource()
        val platform = AndroidPlatformSettings(push, prefs(), SpeechAvailability.Status.Ready)
        val cases = listOf(
            PushStatus.Waiting to RowStatus.PUSH_WAITING,
            PushStatus.Failed("x") to RowStatus.PUSH_FAILED,
            PushStatus.NotificationsOff to RowStatus.PUSH_OFF,
        )
        for ((status, row) in cases) {
            push.status.value = status
            assertEquals(row, platform.rows.first().first().status)
        }
    }

    @Test
    fun demoPushHasNoWord() = runTest(dispatcher) {
        val platform = AndroidPlatformSettings(FakePushStatusSource(PushStatus.Demo), prefs(), SpeechAvailability.Status.Ready)
        assertEquals(PlatformRow(SettingsPage.NOTIFICATIONS, RowStatus.NONE), platform.rows.first().first())
    }

    @Test
    fun voiceIsOffWithoutOnDeviceRecognition() = runTest(dispatcher) {
        val prefs = prefs()
        prefs.setVoiceEnabled(true)
        prefs.voiceEnabled.first { it == true }
        val platform = AndroidPlatformSettings(FakePushStatusSource(), prefs, SpeechAvailability.Status.NoOnDevice)
        assertEquals(PlatformRow(SettingsPage.VOICE, RowStatus.OFF), platform.rows.first().last())
        assertEquals(PlatformRow(SettingsPage.VOICE, RowStatus.OFF), platform.currentRows().last())
    }

    @Test
    fun refreshRereadsPush() {
        val push = FakePushStatusSource()
        AndroidPlatformSettings(push, prefs(), SpeechAvailability.Status.Ready).refresh()
        assertEquals(1, push.refreshes)
    }
}
