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
        var overdue = 0
        val endpointer = SpeechEndpointer(
            scope.backgroundScope,
            onSilence = { silences++ },
            onNothingHeard = { nothingHeard++ },
            onFinalOverdue = { overdue++ },
        )
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

    @Test
    fun finalOverdueFiresAfterSilenceWhenNothingArrives() = runTest {
        val h = Harness(this)
        h.endpointer.start()
        h.endpointer.heard()
        advanceTimeBy(SILENCE_END_MS + 1)
        assertEquals(1, h.silences)
        assertEquals(0, h.overdue)
        advanceTimeBy(FINAL_WAIT_MS + 1)
        assertEquals(1, h.overdue)
        advanceTimeBy(10_000)
        assertEquals(1, h.silences)
        assertEquals(1, h.overdue)
        assertEquals(0, h.nothingHeard)
    }

    @Test
    fun stopAfterSilenceCancelsTheFinalWait() = runTest {
        val h = Harness(this)
        h.endpointer.start()
        h.endpointer.heard()
        advanceTimeBy(SILENCE_END_MS + 1)
        assertEquals(1, h.silences)
        h.endpointer.stop()
        advanceTimeBy(10_000)
        assertEquals(0, h.overdue)
    }
}
