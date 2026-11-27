package com.gekkotron.nidoo.feature.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val BUS = 0uL
private const val TAP_FRAMES = 1024u

/**
 * Listens with SFSpeechRecognizer, on the phone only (iOS spec §7.5, as on Android), in [localeTag]. The recognizer
 * doesn't end the sentence on silence, so a [SpeechEndpointer] ends the audio [SILENCE_END_MS] after the last partial
 * and reports [SpeechError.NO_SPEECH] when nothing is heard within [NO_SPEECH_MS].
 *
 * [start] and [cancel] run on the main thread, like the voice session. The recognizer and the audio tap call back on
 * other queues: every callback is posted to the main queue, and only while its own [start] is current, so a cancelled
 * listen stays silent. At most one final result or error is delivered per [start].
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeechCapture(private val localeTag: String = VOICE_LOCALE) : SpeechCapture {
    private val engine = AVAudioEngine()

    /** Held while listening: a task whose recognizer is released can be cancelled under it. */
    private var recognizer: SFSpeechRecognizer? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var tapInstalled = false
    private var endpointerScope: CoroutineScope? = null
    private var endpointer: SpeechEndpointer? = null

    /** Bumped by every [start] and [cancel]: a callback from an older listen sees a different value and is dropped. */
    private var generation = 0

    override fun start(callbacks: SpeechCapture.Callbacks) {
        cancel()
        val gen = generation
        var ended = false

        /** Ends this listen once: the microphone is released before [deliver] runs, which may start the next one. */
        fun end(deliver: () -> Unit) {
            if (gen != generation || ended) return
            ended = true
            cancel()
            deliver()
        }

        fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

        val recognizer = speechRecognizerFor(localeTag)
        if (recognizer == null || !activateVoiceAudio()) {
            onMain { end { callbacks.onError(SpeechError.SERVICE_UNAVAILABLE) } }
            return
        }
        this.recognizer = recognizer
        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            requiresOnDeviceRecognition = true
        }
        this.request = request
        val input = engine.inputNode
        val format = input.outputFormatForBus(BUS)
        // No microphone (or none routed): installing a tap in a 0 Hz format raises an Objective-C exception.
        if (format.sampleRate <= 0.0 || format.channelCount == 0u) {
            onMain { end { callbacks.onError(SpeechError.SERVICE_UNAVAILABLE) } }
            return
        }
        input.installTapOnBus(BUS, bufferSize = TAP_FRAMES, format = format) { buffer, _ ->
            if (buffer != null) request.appendAudioPCMBuffer(buffer)
        }
        tapInstalled = true
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            onMain { end { callbacks.onError(SpeechError.SERVICE_UNAVAILABLE) } }
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        endpointerScope = scope
        val endpointer = SpeechEndpointer(
            scope,
            onSilence = { if (gen == generation && !ended) stopAudio() },
            onNothingHeard = { end { callbacks.onError(SpeechError.NO_SPEECH) } },
        )
        this.endpointer = endpointer

        task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            val text = result?.bestTranscription?.formattedString.orEmpty()
            val final = result?.final == true
            val failure = error?.let { speechErrorFor(it.domain.orEmpty(), it.code) }
            onMain {
                when {
                    final -> end { if (text.isBlank()) callbacks.onError(SpeechError.NO_SPEECH) else callbacks.onFinal(text) }
                    failure != null -> end { callbacks.onError(failure) }
                    gen == generation && !ended && text.isNotBlank() -> {
                        callbacks.onPartial(text)
                        endpointer.heard()
                    }
                }
            }
        }
        endpointer.start()
    }

    /** Stops listening; the audio session stays active, so the reply can play (see [deactivateVoiceAudio]). */
    override fun cancel() {
        generation++
        endpointer?.stop()
        endpointer = null
        endpointerScope?.cancel()
        endpointerScope = null
        task?.cancel()
        task = null
        stopAudio()
        request = null
        recognizer = null
    }

    /** The sentence is over: no more audio, and the recognizer delivers its final result. */
    private fun stopAudio() {
        if (tapInstalled) {
            engine.inputNode.removeTapOnBus(BUS)
            tapInstalled = false
        }
        if (engine.running) engine.stop()
        request?.endAudio()
    }
}

/**
 * iOS speech errors to the sheet's kinds. kAFAssistantErrorDomain 1110 is "no speech detected" and 203 an empty
 * utterance; 1700 is the recognition service failing. kLSRErrorDomain 102 is the on-device assets missing.
 */
internal fun speechErrorFor(domain: String, code: Long): SpeechError = when (domain) {
    "kAFAssistantErrorDomain" -> when (code) {
        1110L, 203L -> SpeechError.NO_SPEECH
        1700L -> SpeechError.SERVICE_UNAVAILABLE
        else -> SpeechError.UNKNOWN
    }
    "kLSRErrorDomain" -> when (code) {
        102L -> SpeechError.LANGUAGE_UNAVAILABLE
        else -> SpeechError.UNKNOWN
    }
    else -> SpeechError.UNKNOWN
}

/**
 * Listening and the spoken reply share one session: PlayAndRecord, with the reply on the loudspeaker (DefaultToSpeaker;
 * without it the reply plays in the earpiece) and other audio ducked meanwhile. False when iOS refuses it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun activateVoiceAudio(): Boolean {
    val session = AVAudioSession.sharedInstance()
    return session.setCategory(
        AVAudioSessionCategoryPlayAndRecord,
        mode = AVAudioSessionModeDefault,
        options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionDuckOthers,
        error = null,
    ) && session.setActive(true, error = null)
}

/** Hands the audio back when the voice sheet closes (other apps' audio resumes). Errors are ignored. */
@OptIn(ExperimentalForeignApi::class)
fun deactivateVoiceAudio() {
    AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
}
