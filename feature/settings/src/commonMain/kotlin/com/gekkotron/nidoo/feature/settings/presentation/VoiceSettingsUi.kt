package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.feature.voice.SpeechAvailability

data class VoiceSettingsUi(
    val enabled: Boolean = false,
    val availability: SpeechAvailability.Status = SpeechAvailability.Status.Ready,
)
