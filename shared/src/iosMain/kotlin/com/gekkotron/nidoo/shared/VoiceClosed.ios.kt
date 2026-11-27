package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.feature.voice.deactivateVoiceAudio

actual fun onVoiceClosed() = deactivateVoiceAudio()
