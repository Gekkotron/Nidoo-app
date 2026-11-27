package com.gekkotron.nidoo.assist

import com.gekkotron.nidoo.transport.HttpGetResult
import com.gekkotron.nidoo.transport.HttpPostResult
import kotlinx.coroutines.CancellationException

private const val UNPROCESSABLE = 422
private val SPEECH_PATH = Regex("""^/api/v1/assist/speech/[A-Za-z0-9_-]+\.wav$""")

/** Over the hub's `POST /api/v1/assist` and `/api/v1/assist/speech` routes. */
class HttpAssistClient(
    private val baseUrl: suspend () -> String?,
    private val clientId: suspend () -> String,
    private val post: suspend (url: String, json: String) -> HttpPostResult,
    private val get: suspend (url: String, maxBytes: Long) -> HttpGetResult,
) : AssistClient {
    override suspend fun send(text: String): AssistResult = turn { AssistJson.textRequest(clientId(), text) }

    override suspend fun answer(reply: AssistReply): AssistResult = turn { AssistJson.replyRequest(clientId(), reply) }

    override suspend fun speech(text: String): ByteArray? {
        if (text.isBlank() || text.length > MAX_SPEECH_CHARS) return null
        val base = baseUrl()?.trimEnd('/') ?: return null
        return try {
            val posted = post("$base/api/v1/assist/speech", AssistJson.speechRequest(text))
            if (posted !is HttpPostResult.Response || posted.status != 201 && posted.status != 200) return null
            // Only the hub's own speech route: the answer never sends the phone to another host or route.
            val path = AssistJson.speechUrl(posted.body)?.takeIf { SPEECH_PATH.matches(it) } ?: return null
            val downloaded = get(base + path, MAX_WAV_BYTES)
            if (downloaded is HttpGetResult.Response && downloaded.status in 200..299) downloaded.bytes else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun turn(body: suspend () -> String): AssistResult {
        val base = baseUrl()?.trimEnd('/') ?: return AssistResult.NoHub
        return try {
            when (val r = post("$base/api/v1/assist", body())) {
                HttpPostResult.NotAllowed -> AssistResult.GatewayRefused
                is HttpPostResult.Unavailable -> AssistResult.Unreachable
                is HttpPostResult.Response -> when {
                    r.status == 200 -> AssistJson.parseTurn(r.body)?.let { AssistResult.Ok(it) } ?: AssistResult.Failed(r.status)
                    r.status == UNPROCESSABLE && AssistJson.errorText(r.body)?.contains("question expired") == true -> AssistResult.QuestionExpired
                    else -> AssistResult.Failed(r.status)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AssistResult.Unreachable
        }
    }
}
