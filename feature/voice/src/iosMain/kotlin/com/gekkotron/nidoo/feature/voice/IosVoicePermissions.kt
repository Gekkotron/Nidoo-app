package com.gekkotron.nidoo.feature.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.coroutines.resume

/**
 * The microphone, then speech recognition (Info.plist's NSMicrophoneUsageDescription and
 * NSSpeechRecognitionUsageDescription). iOS prompts once for each; an answer already given comes back at once, without
 * a prompt. A refused microphone ends here: recognition alone is no use.
 */
object IosVoicePermissions : VoicePermissions {
    override suspend fun request(): Boolean = microphone() && speechRecognition()

    private suspend fun microphone(): Boolean = suspendCancellableCoroutine { cont ->
        AVAudioSession.sharedInstance().requestRecordPermission { granted -> if (cont.isActive) cont.resume(granted) }
    }

    private suspend fun speechRecognition(): Boolean = suspendCancellableCoroutine { cont ->
        SFSpeechRecognizer.requestAuthorization { status: SFSpeechRecognizerAuthorizationStatus ->
            if (cont.isActive) cont.resume(status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized)
        }
    }
}
