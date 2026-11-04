package com.gekkotron.nidoo.assist

sealed interface AssistResult {
    data class Ok(val turn: AssistTurn) : AssistResult
    data object NoHub : AssistResult
    data object Unreachable : AssistResult
    data object GatewayRefused : AssistResult
    data object QuestionExpired : AssistResult
    data class Failed(val status: Int?) : AssistResult
}

interface AssistClient {
    suspend fun send(text: String): AssistResult

    suspend fun answer(reply: AssistReply): AssistResult

    /** The full WAV, or null on any failure (no hub voice, queue full, too long, network). */
    suspend fun speech(text: String): ByteArray?
}
