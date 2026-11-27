package com.gekkotron.nidoo.feature.voice

import platform.Foundation.NSLocale
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus

/** Ready when [localeTag] has a recognizer that runs on the phone; iOS listens on-device only, as Android does. */
fun iosSpeechAvailability(localeTag: String = VOICE_LOCALE): SpeechAvailability.Status {
    val recognizer = speechRecognizerFor(localeTag)
    return availabilityOf(
        recognizerExists = recognizer != null,
        supportsOnDevice = recognizer?.supportsOnDeviceRecognition == true,
        authorizationUndetermined = SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined,
    )
}

/**
 * Before speech authorization iOS may report no on-device support, and we can't know until the user is asked,
 * so an existing recognizer with undetermined authorization counts as Ready.
 */
internal fun availabilityOf(
    recognizerExists: Boolean,
    supportsOnDevice: Boolean,
    authorizationUndetermined: Boolean,
): SpeechAvailability.Status =
    if (recognizerExists && (supportsOnDevice || authorizationUndetermined)) {
        SpeechAvailability.Status.Ready
    } else {
        SpeechAvailability.Status.NoOnDevice
    }

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
