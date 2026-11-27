package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePlatformSettingsTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-voice-rows-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun off() = listOf(PlatformRow(SettingsPage.VOICE, RowStatus.OFF))
    private fun on() = listOf(PlatformRow(SettingsPage.VOICE, RowStatus.ON))

    @Test
    fun voiceRowFollowsThePreference() = runTest {
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        preferences.voiceEnabled.first { it != null }
        val settings = VoicePlatformSettings(preferences, SpeechAvailability.Status.Ready)
        assertEquals(off(), settings.currentRows())
        preferences.setVoiceEnabled(true)
        assertEquals(on(), settings.rows.first { it == on() })
        settings.refresh()
    }

    @Test
    fun notReadyStaysOff() = runTest {
        val preferences = AppPreferences.create(dir.resolve(AppPreferences.FILE), backgroundScope)
        preferences.voiceEnabled.first { it != null }
        preferences.setVoiceEnabled(true)
        preferences.voiceEnabled.first { it == true }
        val settings = VoicePlatformSettings(preferences, SpeechAvailability.Status.NoOnDevice)
        assertEquals(off(), settings.currentRows())
        assertEquals(off(), settings.rows.first())
    }
}
