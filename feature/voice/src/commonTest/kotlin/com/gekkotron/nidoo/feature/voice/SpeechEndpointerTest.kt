package com.gekkotron.nidoo.feature.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechEndpointerTest {
    private class Harness(scope: TestScope) {
        var silences = 0
        var nothingHeard = 0
        val endpointer = SpeechEndpointer(scope.backgroundScope, onSilence = { silences++ }, onNothingHeard = { nothingHeard++ })
    }

    @Test
    fun nothingHeardEndsAsNoSpeech() = runTest {
        val h = Harness(this)
        h.endpointer.start()
        advanceTimeBy(NO_SPEECH_MS + 1)
        assertEquals(1, h.nothingHeard)
        assertEquals(0, h.silences)
    }

    @Test
    fun eachPartialRestartsTheSilenceWindow() = runTest {
        val h = Harness(this)
        h.endpointer.start()
        h.endpointer.heard()
        advanceTimeBy(1_000)
        h.endpointer.heard()
        advanceTimeBy(1_000)
        assertEquals(0, h.silences)
        advanceTimeBy(600)
        assertEquals(1, h.silences)
        advanceTimeBy(NO_SPEECH_MS)
        assertEquals(1, h.silences)
        assertEquals(0, h.nothingHeard)
    }

    @Test
    fun stopSilencesEverything() = runTest {
        val h = Harness(this)
        h.endpointer.start()
        h.endpointer.heard()
        h.endpointer.stop()
        advanceTimeBy(10_000)
        assertEquals(0, h.silences)
        assertEquals(0, h.nothingHeard)
    }
}
