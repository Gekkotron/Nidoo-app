package com.gekkotron.nidoo.feature.voice

import com.gekkotron.nidoo.assist.AssistClient
import com.gekkotron.nidoo.assist.AssistOutcome
import com.gekkotron.nidoo.assist.AssistReply
import com.gekkotron.nidoo.assist.AssistResult
import com.gekkotron.nidoo.assist.AssistTurn
import com.gekkotron.nidoo.assist.QuestionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val QUESTION_TTL_MS = 60_000L
const val AUDIO_TAIL_MS = 1_500L
const val NO_AUDIO_CLOSE_MS = 5_000L

sealed interface VoiceSessionState {
    data class Listening(val partial: String = "", val answering: Boolean = false) : VoiceSessionState
    data class Sending(val heard: String?) : VoiceSessionState
    data class Turn(val turn: AssistTurn, val expired: Boolean = false) : VoiceSessionState
    data class Error(val kind: ErrorKind) : VoiceSessionState
}

/** [DISABLED]: the opt-in voice assistant is off (e.g. a widget drawn before it was turned off). */
enum class ErrorKind { NO_HUB, HUB_UNREACHABLE, GATEWAY_REFUSED, HUB_FAILED, SPEECH, PERMISSION, SILENCE, DISABLED }

/**
 * One voice turn at a time: listen, send the sentence to the hub's assistant, show its turn, play its voice.
 * Every new action cancels the work of the previous one; [generation] discards results that still arrive.
 */
class VoiceSession(
    private val client: AssistClient,
    private val speech: SpeechCapture,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Listening())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()
    private val _closeRequested = MutableStateFlow(false)
    val closeRequested: StateFlow<Boolean> = _closeRequested.asStateFlow()
    private val _canRepeat = MutableStateFlow(false)
    val canRepeat: StateFlow<Boolean> = _canRepeat.asStateFlow()

    private var generation = 0
    private var work: Job? = null
    private var audio: Job? = null
    private var lastWav: ByteArray? = null
    private var lastTurn: AssistTurn? = null
    private val answered = mutableSetOf<String>()

    fun start() = listen(answering = false)

    fun choose(index: Int) {
        val question = openQuestion() ?: return
        if (question.kind != QuestionKind.CHOICE || index !in question.choices.indices) return
        answer(question.pending, AssistReply.Choice(question.pending, index))
    }

    fun confirm(yes: Boolean) {
        val question = openQuestion() ?: return
        if (question.kind != QuestionKind.CONFIRM) return
        answer(question.pending, if (yes) AssistReply.Yes(question.pending) else AssistReply.No(question.pending))
    }

    fun listenAgain() = listen(answering = openQuestion() != null)

    fun repeat() {
        val wav = lastWav ?: return
        val parent = work ?: return
        audio?.cancel()
        player.stop()
        audio = scope.launch(parent) { player.play(wav) }
    }

    fun cancel() {
        beginAction()
        speech.cancel()
    }

    private fun openQuestion() = (_state.value as? VoiceSessionState.Turn)?.takeIf { !it.expired }?.turn?.question

    /** Invalidates everything in flight; returns the generation of the new action. */
    private fun beginAction(): Int {
        generation++
        work?.cancel()
        work = Job(scope.coroutineContext[Job])
        audio = null
        player.stop()
        lastWav = null
        _canRepeat.value = false
        _closeRequested.value = false
        return generation
    }

    private fun listen(answering: Boolean) {
        val gen = beginAction()
        speech.cancel()
        _state.value = VoiceSessionState.Listening(answering = answering)
        speech.start(object : SpeechCapture.Callbacks {
            override fun onPartial(text: String) {
                if (gen == generation) _state.value = VoiceSessionState.Listening(text, answering)
            }

            override fun onFinal(text: String) {
                if (gen == generation) send(text)
            }

            override fun onError(kind: SpeechError) {
                if (gen != generation) return
                _state.value = VoiceSessionState.Error(
                    when (kind) {
                        SpeechError.NO_SPEECH -> ErrorKind.SILENCE
                        SpeechError.PERMISSION_DENIED -> ErrorKind.PERMISSION
                        else -> ErrorKind.SPEECH
                    },
                )
            }
        })
    }

    private fun send(text: String) {
        val gen = beginAction()
        _state.value = VoiceSessionState.Sending(text)
        scope.launch(work!!) { handle(gen, client.send(text)) }
    }

    private fun answer(pending: String, reply: AssistReply) {
        if (!answered.add(pending)) return
        val gen = beginAction()
        _state.value = VoiceSessionState.Sending(null)
        scope.launch(work!!) { handle(gen, client.answer(reply)) }
    }

    private suspend fun handle(gen: Int, result: AssistResult) {
        if (gen != generation) return
        when (result) {
            is AssistResult.Ok -> showTurn(gen, result.turn)
            AssistResult.NoHub -> fail(ErrorKind.NO_HUB)
            AssistResult.Unreachable -> fail(ErrorKind.HUB_UNREACHABLE)
            AssistResult.GatewayRefused -> fail(ErrorKind.GATEWAY_REFUSED)
            AssistResult.QuestionExpired -> {
                val turn = lastTurn
                _state.value = if (turn != null) VoiceSessionState.Turn(turn, expired = true) else VoiceSessionState.Error(ErrorKind.HUB_FAILED)
            }
            is AssistResult.Failed -> fail(ErrorKind.HUB_FAILED)
        }
    }

    private fun fail(kind: ErrorKind) { _state.value = VoiceSessionState.Error(kind) }

    private fun showTurn(gen: Int, turn: AssistTurn) {
        val arrived = clock()
        val parent = work ?: return
        lastTurn = turn
        _state.value = VoiceSessionState.Turn(turn)
        fun remaining(total: Long) = (total - (clock() - arrived)).coerceAtLeast(0)
        if (turn.question != null) {
            scope.launch(parent) {
                delay(remaining(QUESTION_TTL_MS))
                if (gen == generation) _state.value = VoiceSessionState.Turn(turn, expired = true)
            }
        }
        // The reply is already on screen; its voice is fetched and played after.
        audio = scope.launch(parent) {
            val wav = client.speech(turn.reply)
            if (gen != generation) return@launch
            val played = if (wav != null) {
                lastWav = wav
                _canRepeat.value = true
                player.play(wav)
            } else {
                false
            }
            if (gen != generation) return@launch
            val closes = turn.question == null && (turn.outcome == AssistOutcome.ACTED || turn.outcome == AssistOutcome.ANSWERED)
            if (closes) {
                delay(if (played) AUDIO_TAIL_MS else remaining(NO_AUDIO_CLOSE_MS))
                if (gen == generation) _closeRequested.value = true
            }
        }
    }
}
