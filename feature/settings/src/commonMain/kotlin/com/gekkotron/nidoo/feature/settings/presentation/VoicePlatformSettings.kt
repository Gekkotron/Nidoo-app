package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** iOS's one extra House row: Voice, on only when it is enabled and the phone can listen (Android's rule). */
class VoicePlatformSettings(
    private val preferences: AppPreferences,
    private val availability: SpeechAvailability.Status,
) : PlatformSettings {
    override val rows: Flow<List<PlatformRow>> = preferences.voiceEnabled.map { rowsOf(it == true) }

    override fun currentRows(): List<PlatformRow> = rowsOf(preferences.voiceEnabled.value == true)

    override fun refresh() = Unit

    private fun rowsOf(voiceOn: Boolean) = listOf(
        PlatformRow(SettingsPage.VOICE, if (voiceOn && availability == SpeechAvailability.Status.Ready) RowStatus.ON else RowStatus.OFF),
    )
}
