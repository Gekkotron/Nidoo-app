package com.gekkotron.nidoo.feature.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The sentence ends this long after the last partial result. */
const val SILENCE_END_MS = 1_500L

/** Listening ends as "no speech" when nothing is heard this long after it starts. */
const val NO_SPEECH_MS = 6_000L

/** After the sentence ends, the recognizer's final result is overdue this long after; listening must never hang. */
const val FINAL_WAIT_MS = 2_500L

/**
 * Ends a sentence for a recognizer that doesn't (iOS's SFSpeechRecognizer keeps listening through silence).
 * [start] arms the no-speech timer; each [heard] cancels it and restarts the silence timer. Only one of [onSilence] and
 * [onNothingHeard] fires, at most once. After [onSilence], a [FINAL_WAIT_MS] timer fires [onFinalOverdue] once unless
 * [stop] comes first (the recognizer's final result or error arrived). Nothing fires after [stop].
 * Not thread-safe: call it from [scope]'s thread.
 */
class SpeechEndpointer(
    private val scope: CoroutineScope,
    private val onSilence: () -> Unit,
    private val onNothingHeard: () -> Unit,
    private val onFinalOverdue: () -> Unit,
) {
    private var noSpeech: Job? = null
    private var silence: Job? = null
    private var finalWait: Job? = null
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
            silenceEnded()
        }
    }

    fun stop() {
        ended = true
        noSpeech?.cancel()
        silence?.cancel()
        finalWait?.cancel()
        noSpeech = null
        silence = null
        finalWait = null
    }

    private fun silenceEnded() {
        if (ended) return
        stop()
        finalWait = scope.launch {
            delay(FINAL_WAIT_MS)
            onFinalOverdue()
        }
        onSilence()
    }

    private fun end(callback: () -> Unit) {
        if (ended) return
        stop()
        callback()
    }
}
