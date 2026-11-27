package com.gekkotron.nidoo.feature.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The sentence ends this long after the last partial result. */
const val SILENCE_END_MS = 1_500L

/** Listening ends as "no speech" when nothing is heard this long after it starts. */
const val NO_SPEECH_MS = 6_000L

/**
 * Ends a sentence for a recognizer that doesn't (iOS's SFSpeechRecognizer keeps listening through silence).
 * [start] arms the no-speech timer; each [heard] cancels it and restarts the silence timer. Only one of the two
 * callbacks fires, at most once, and none after [stop]. Not thread-safe: call it from [scope]'s thread.
 */
class SpeechEndpointer(
    private val scope: CoroutineScope,
    private val onSilence: () -> Unit,
    private val onNothingHeard: () -> Unit,
) {
    private var noSpeech: Job? = null
    private var silence: Job? = null
    private var ended = false

    fun start() {
        stop()
        ended = false
        noSpeech = scope.launch {
            delay(NO_SPEECH_MS)
            end(onNothingHeard)
        }
    }

    fun heard() {
        if (ended) return
        noSpeech?.cancel()
        noSpeech = null
        silence?.cancel()
        silence = scope.launch {
            delay(SILENCE_END_MS)
            end(onSilence)
        }
    }

    fun stop() {
        ended = true
        noSpeech?.cancel()
        silence?.cancel()
        noSpeech = null
        silence = null
    }

    private fun end(callback: () -> Unit) {
        if (ended) return
        stop()
        callback()
    }
}
