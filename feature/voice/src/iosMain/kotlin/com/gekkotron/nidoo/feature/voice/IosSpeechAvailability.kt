package com.gekkotron.nidoo.feature.voice

import platform.Foundation.NSLocale
import platform.Speech.SFSpeechRecognizer

/** Ready when [localeTag] has a recognizer that runs on the phone; iOS listens on-device only, as Android does. */
fun iosSpeechAvailability(localeTag: String = VOICE_LOCALE): SpeechAvailability.Status {
    val recognizer = speechRecognizerFor(localeTag)
    return availabilityOf(recognizer != null, recognizer?.supportsOnDeviceRecognition == true)
}

internal fun availabilityOf(recognizerExists: Boolean, supportsOnDevice: Boolean): SpeechAvailability.Status =
    if (recognizerExists && supportsOnDevice) SpeechAvailability.Status.Ready else SpeechAvailability.Status.NoOnDevice

/** The recognizer for [localeTag], or null when iOS has none for it. */
internal fun speechRecognizerFor(localeTag: String): SFSpeechRecognizer? =
    orNilInit { SFSpeechRecognizer(locale = NSLocale(localeIdentifier = localeTag)) }

/** Kotlin/Native turns an Objective-C initializer that returns nil into a NullPointerException; this makes it null. */
internal inline fun <T : Any> orNilInit(make: () -> T): T? =
    try {
        make()
    } catch (e: NullPointerException) {
        null
    }
