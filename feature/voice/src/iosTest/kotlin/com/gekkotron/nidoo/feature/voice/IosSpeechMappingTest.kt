package com.gekkotron.nidoo.feature.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class IosSpeechMappingTest {
    @Test
    fun noSpeechCodesMapToNoSpeech() {
        assertEquals(SpeechError.NO_SPEECH, speechErrorFor("kAFAssistantErrorDomain", 1110))
        assertEquals(SpeechError.NO_SPEECH, speechErrorFor("kAFAssistantErrorDomain", 203))
    }

    @Test
    fun missingOnDeviceAssetsMapToLanguageUnavailable() {
        assertEquals(SpeechError.LANGUAGE_UNAVAILABLE, speechErrorFor("kLSRErrorDomain", 102))
    }

    @Test
    fun serviceFailureMapsToServiceUnavailable() {
        assertEquals(SpeechError.SERVICE_UNAVAILABLE, speechErrorFor("kAFAssistantErrorDomain", 1700))
    }

    @Test
    fun anyOtherErrorIsUnknown() {
        assertEquals(SpeechError.UNKNOWN, speechErrorFor("NSCocoaErrorDomain", 1))
    }

    @Test
    fun onDeviceRecognizerIsReady() {
        assertEquals(SpeechAvailability.Status.Ready, availabilityOf(recognizerExists = true, supportsOnDevice = true))
    }

    @Test
    fun serverOnlyOrMissingRecognizerIsNoOnDevice() {
        assertEquals(SpeechAvailability.Status.NoOnDevice, availabilityOf(recognizerExists = true, supportsOnDevice = false))
        assertEquals(SpeechAvailability.Status.NoOnDevice, availabilityOf(recognizerExists = false, supportsOnDevice = false))
    }
}
