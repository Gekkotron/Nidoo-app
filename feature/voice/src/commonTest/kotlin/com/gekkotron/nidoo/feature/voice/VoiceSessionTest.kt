package com.gekkotron.nidoo.feature.voice

import com.gekkotron.nidoo.assist.AssistOutcome
import com.gekkotron.nidoo.assist.AssistQuestion
import com.gekkotron.nidoo.assist.AssistReply
import com.gekkotron.nidoo.assist.AssistResult
import com.gekkotron.nidoo.assist.AssistTurn
import com.gekkotron.nidoo.assist.QuestionKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionTest {
    private val wav = byteArrayOf(1, 2, 3)

    private fun turn(
        outcome: AssistOutcome = AssistOutcome.ACTED,
        reply: String = "C'est fait.",
        question: AssistQuestion? = null,
    ) = AssistTurn("t1", "allume le salon", outcome, reply, question)

    private val choiceQuestion = AssistQuestion("p1", QuestionKind.CHOICE, listOf("Chambre Léa", "Chambre parentale"))
    private val confirmQuestion = AssistQuestion("p2", QuestionKind.CONFIRM, emptyList())

    private class Harness(scope: TestScope) {
        val speech = FakeSpeechCapture()
        val client = FakeAssistClient()
        val player = FakePlayer()
        val session = VoiceSession(client, speech, player, scope.backgroundScope) { scope.testScheduler.currentTime }
    }

    private suspend fun TestScope.harness(): Harness = Harness(this)

    private fun TestScope.hear(h: Harness, text: String = "allume le salon") {
        h.session.start()
        runCurrent()
        h.speech.emitFinal(text)
        runCurrent()
    }

    @Test fun `a heard sentence is sent and the turn is shown`() = runTest {
        val h = harness()
        val t = turn(AssistOutcome.ANSWERED)
        h.client.results += AssistResult.Ok(t)
        hear(h)
        assertEquals(listOf("allume le salon"), h.client.sent)
        assertEquals(VoiceSessionState.Turn(t), h.session.state.value)
    }

    @Test fun `sending shows the heard text`() = runTest {
        val h = harness()
        h.client.gate = CompletableDeferred()
        h.client.results += AssistResult.Ok(turn())
        hear(h)
        assertEquals(VoiceSessionState.Sending("allume le salon"), h.session.state.value)
    }

    @Test fun `an acted turn plays the hub audio then requests close 1500 ms after it ends`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn())
        h.client.speechResults += wav
        hear(h)
        assertContentEquals(wav, h.player.played.single())
        assertFalse(h.session.closeRequested.value)
        advanceTimeBy(10_000)
        assertFalse(h.session.closeRequested.value)
        h.player.finish()
        runCurrent()
        advanceTimeBy(AUDIO_TAIL_MS - 1)
        assertFalse(h.session.closeRequested.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(h.session.closeRequested.value)
    }

    @Test fun `an answered turn without audio requests close 5000 ms after the turn`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.ANSWERED))
        hear(h)
        advanceTimeBy(NO_AUDIO_CLOSE_MS - 1)
        assertFalse(h.session.closeRequested.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(h.session.closeRequested.value)
    }

    @Test fun `a choice tap sends Choice with the pending id and shows the next turn`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choiceQuestion))
        hear(h)
        val next = turn(AssistOutcome.ACTED)
        h.client.results += AssistResult.Ok(next)
        h.session.choose(1)
        runCurrent()
        assertEquals(listOf<AssistReply>(AssistReply.Choice("p1", 1)), h.client.answers)
        assertEquals(VoiceSessionState.Turn(next), h.session.state.value)
    }

    @Test fun `a confirm tap sends Yes or No`() = runTest {
        val yes = harness()
        yes.client.results += AssistResult.Ok(turn(AssistOutcome.CONFIRMING, "Désarmer ?", confirmQuestion))
        hear(yes)
        yes.client.results += AssistResult.Ok(turn())
        yes.session.confirm(true)
        runCurrent()
        assertEquals(listOf<AssistReply>(AssistReply.Yes("p2")), yes.client.answers)

        val no = harness()
        no.client.results += AssistResult.Ok(turn(AssistOutcome.CONFIRMING, "Désarmer ?", confirmQuestion))
        hear(no)
        no.client.results += AssistResult.Ok(turn())
        no.session.confirm(false)
        runCurrent()
        assertEquals(listOf<AssistReply>(AssistReply.No("p2")), no.client.answers)
    }

    @Test fun `a second tap on the same question is ignored`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choiceQuestion))
        hear(h)
        h.client.gate = CompletableDeferred()
        h.client.results += AssistResult.Ok(turn())
        h.session.choose(0)
        h.session.choose(1)
        runCurrent()
        assertEquals(1, h.client.answers.size)
    }

    @Test fun `a question does not request close and expires after 60 s`() = runTest {
        val h = harness()
        val t = turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choiceQuestion)
        h.client.results += AssistResult.Ok(t)
        hear(h)
        advanceTimeBy(QUESTION_TTL_MS - 1)
        assertEquals(VoiceSessionState.Turn(t), h.session.state.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VoiceSessionState.Turn(t, expired = true), h.session.state.value)
        assertFalse(h.session.closeRequested.value)
    }

    @Test fun `a tap answered QuestionExpired shows the turn as expired`() = runTest {
        val h = harness()
        val t = turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choiceQuestion)
        h.client.results += AssistResult.Ok(t)
        hear(h)
        h.client.results += AssistResult.QuestionExpired
        h.session.choose(0)
        runCurrent()
        assertEquals(VoiceSessionState.Turn(t, expired = true), h.session.state.value)
    }

    @Test fun `not understood stays open`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.NOT_UNDERSTOOD, "Je n'ai pas compris."))
        hear(h)
        advanceTimeBy(10_000)
        assertFalse(h.session.closeRequested.value)
    }

    @Test fun `hub failures map to their error kinds`() = runTest {
        val expected = listOf(
            AssistResult.NoHub to ErrorKind.NO_HUB,
            AssistResult.Unreachable to ErrorKind.HUB_UNREACHABLE,
            AssistResult.GatewayRefused to ErrorKind.GATEWAY_REFUSED,
            AssistResult.Failed(500) to ErrorKind.HUB_FAILED,
        )
        for ((result, kind) in expected) {
            val h = harness()
            h.client.results += result
            hear(h)
            assertEquals(VoiceSessionState.Error(kind), h.session.state.value)
        }
    }

    @Test fun `speech failure leaves the turn and no error`() = runTest {
        val h = harness()
        val t = turn(AssistOutcome.NOT_UNDERSTOOD, "Je n'ai pas compris.")
        h.client.results += AssistResult.Ok(t)
        h.client.speechResults += null
        hear(h)
        assertEquals(VoiceSessionState.Turn(t), h.session.state.value)
        assertFalse(h.session.canRepeat.value)
        assertTrue(h.player.played.isEmpty())
    }

    @Test fun `the reply shows before the audio arrives`() = runTest {
        val h = harness()
        val t = turn()
        h.client.results += AssistResult.Ok(t)
        h.client.speechGate = CompletableDeferred()
        h.client.speechResults += wav
        hear(h)
        assertEquals(VoiceSessionState.Turn(t), h.session.state.value)
        assertEquals(listOf("C'est fait."), h.client.speechRequests)
        assertTrue(h.player.played.isEmpty())
    }

    @Test fun `repeat replays the last wav without a request`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.NOT_UNDERSTOOD))
        h.client.speechResults += wav
        hear(h)
        h.player.finish()
        runCurrent()
        assertTrue(h.session.canRepeat.value)
        h.session.repeat()
        runCurrent()
        assertEquals(2, h.player.played.size)
        assertEquals(1, h.client.sent.size)
        assertEquals(1, h.client.speechRequests.size)
    }

    @Test fun `cancel stops playback and drops pending speech`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn())
        val gate = CompletableDeferred<Unit>()
        h.client.speechGate = gate
        h.client.speechResults += wav
        hear(h)
        val stopsBefore = h.player.stops
        h.session.cancel()
        assertTrue(h.player.stops > stopsBefore)
        gate.complete(Unit)
        runCurrent()
        assertTrue(h.player.played.isEmpty())
    }

    @Test fun `cancel during playback stops the player`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn())
        h.client.speechResults += wav
        hear(h)
        assertEquals(1, h.player.played.size)
        val stopsBefore = h.player.stops
        h.session.cancel()
        assertTrue(h.player.stops > stopsBefore)
        advanceTimeBy(10_000)
        assertFalse(h.session.closeRequested.value)
    }

    @Test fun `a stale answer does not overwrite a newer listen`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choiceQuestion))
        hear(h)
        h.client.gate = CompletableDeferred()
        h.client.results += AssistResult.Ok(turn())
        h.session.choose(0)
        runCurrent()
        h.session.listenAgain()
        runCurrent()
        h.client.gate!!.complete(Unit)
        runCurrent()
        assertIs<VoiceSessionState.Listening>(h.session.state.value)
    }

    @Test fun `listen again sends the heard text as text`() = runTest {
        val h = harness()
        h.client.results += AssistResult.Ok(turn(AssistOutcome.CONFIRMING, "Désarmer ?", confirmQuestion))
        hear(h)
        h.session.listenAgain()
        runCurrent()
        assertEquals(VoiceSessionState.Listening("", answering = true), h.session.state.value)
        val next = turn()
        h.client.results += AssistResult.Ok(next)
        h.speech.emitFinal("oui")
        runCurrent()
        assertEquals(listOf("allume le salon", "oui"), h.client.sent)
        assertTrue(h.client.answers.isEmpty())
        assertEquals(VoiceSessionState.Turn(next), h.session.state.value)
    }

    @Test fun `partial text is shown while listening`() = runTest {
        val h = harness()
        h.session.start()
        runCurrent()
        h.speech.emitPartial("allume le")
        assertEquals(VoiceSessionState.Listening("allume le"), h.session.state.value)
    }

    @Test fun `recognizer errors map to SILENCE, PERMISSION, SPEECH`() = runTest {
        val expected = listOf(
            SpeechError.NO_SPEECH to ErrorKind.SILENCE,
            SpeechError.PERMISSION_DENIED to ErrorKind.PERMISSION,
            SpeechError.SERVICE_UNAVAILABLE to ErrorKind.SPEECH,
        )
        for ((error, kind) in expected) {
            val h = harness()
            h.session.start()
            runCurrent()
            h.speech.emitError(error)
            assertEquals(VoiceSessionState.Error(kind), h.session.state.value)
        }
    }
}
