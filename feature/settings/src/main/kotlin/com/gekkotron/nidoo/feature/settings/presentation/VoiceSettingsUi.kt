package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.feature.voice.TtsAvailability
import com.gekkotron.nidoo.voice.VoiceLang

data class ShortcutRow(val id: String, val phrase: String, val lang: VoiceLang, val label: String, val broken: Boolean)

data class VoiceSettingsUi(
    val enabled: Boolean = false,
    val availability: SpeechAvailability.Status = SpeechAvailability.Status.Ready,
    val shortcuts: List<ShortcutRow> = emptyList(),
    val ttsAvailability: TtsAvailability = TtsAvailability.Unknown,
)
