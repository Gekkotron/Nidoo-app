package com.gekkotron.nidoo.feature.voice

/** The recognizer's language on both hosts. */
const val VOICE_LOCALE = "fr-FR"

/** Makes the speech capture for one session; null means the phone can't listen (the sheet shows the speech error). */
fun interface SpeechCaptureFactory {
    fun create(): SpeechCapture?
}

/** Asks for everything listening needs; true when it is all granted. Android asks in its own activity and binds none. */
interface VoicePermissions {
    suspend fun request(): Boolean
}
