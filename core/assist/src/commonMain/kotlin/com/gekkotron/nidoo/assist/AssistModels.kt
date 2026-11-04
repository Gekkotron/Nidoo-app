package com.gekkotron.nidoo.assist

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val MAX_SPEECH_CHARS = 500
const val MAX_WAV_BYTES = 4L * 1024 * 1024

enum class AssistOutcome { ACTED, ANSWERED, ASKED, CONFIRMING, NOT_UNDERSTOOD, FAILED }

enum class QuestionKind { CHOICE, CONFIRM }

data class AssistQuestion(val pending: String, val kind: QuestionKind, val choices: List<String>)

data class AssistTurn(
    val id: String,
    val text: String,
    val outcome: AssistOutcome,
    val reply: String,
    val question: AssistQuestion?,
)

sealed interface AssistReply {
    val pending: String

    data class Choice(override val pending: String, val index: Int) : AssistReply
    data class Yes(override val pending: String) : AssistReply
    data class No(override val pending: String) : AssistReply
}

@Serializable
private class TurnDto(
    val id: String,
    val text: String = "",
    val outcome: String = "",
    val reply: String = "",
    val question: QuestionDto? = null,
)

@Serializable
private class QuestionDto(
    val pending: String,
    val kind: String = "",
    val choices: List<String> = emptyList(),
)

@Serializable
private class SpeechRequest(val text: String)

object AssistJson {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parseTurn(json: String): AssistTurn? = try {
        val dto = this.json.decodeFromString<TurnDto>(json)
        AssistTurn(dto.id, dto.text, outcomeOf(dto.outcome), dto.reply, dto.question?.let(::questionOf))
    } catch (e: IllegalArgumentException) {
        null
    }

    fun textRequest(client: String, text: String): String = buildJsonObject {
        put("client", client)
        put("text", text)
    }.toString()

    fun replyRequest(client: String, reply: AssistReply): String = buildJsonObject {
        put("client", client)
        putJsonObject("reply") {
            when (reply) {
                is AssistReply.Choice -> {
                    put("type", "choice")
                    put("pending", reply.pending)
                    put("choice", reply.index)
                }
                is AssistReply.Yes -> {
                    put("type", "yes")
                    put("pending", reply.pending)
                }
                is AssistReply.No -> {
                    put("type", "no")
                    put("pending", reply.pending)
                }
            }
        }
    }.toString()

    internal fun speechRequest(text: String): String = json.encodeToString(SpeechRequest(text))

    internal fun speechUrl(body: String): String? = try {
        json.parseToJsonElement(body).let { (it as? kotlinx.serialization.json.JsonObject)?.get("url") }
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    } catch (e: IllegalArgumentException) {
        null
    }

    internal fun errorText(body: String): String? = try {
        json.parseToJsonElement(body).let { (it as? kotlinx.serialization.json.JsonObject)?.get("error") }
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun outcomeOf(s: String) = when (s.lowercase()) {
        "acted" -> AssistOutcome.ACTED
        "answered" -> AssistOutcome.ANSWERED
        "asked" -> AssistOutcome.ASKED
        "confirming" -> AssistOutcome.CONFIRMING
        "not_understood" -> AssistOutcome.NOT_UNDERSTOOD
        else -> AssistOutcome.FAILED
    }

    private fun questionOf(q: QuestionDto) = AssistQuestion(
        q.pending,
        if (q.kind.equals("confirm", ignoreCase = true)) QuestionKind.CONFIRM else QuestionKind.CHOICE,
        q.choices,
    )
}
