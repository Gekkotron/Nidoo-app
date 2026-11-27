package com.gekkotron.nidoo.feature.voice

enum class SpeechError { NO_SPEECH, LANGUAGE_UNAVAILABLE, SERVICE_UNAVAILABLE, PERMISSION_DENIED, UNKNOWN }

interface SpeechCapture {
    fun start(callbacks: Callbacks)
    fun cancel()

    interface Callbacks {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(kind: SpeechError)
    }
}
