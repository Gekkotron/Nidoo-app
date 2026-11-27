package com.gekkotron.nidoo.feature.voice

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer

fun androidSpeechAvailability(context: Context): SpeechAvailability.Status {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return SpeechAvailability.Status.NeedsApi31
    return if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) SpeechAvailability.Status.Ready else SpeechAvailability.Status.NoOnDevice
}
