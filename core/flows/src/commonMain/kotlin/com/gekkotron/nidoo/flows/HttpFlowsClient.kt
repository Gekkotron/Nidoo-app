package com.gekkotron.nidoo.flows

import com.gekkotron.nidoo.transport.HttpPostResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private class EnabledBody(val enabled: Boolean)

@Serializable
private class WakeUpBody(val hour: Int, val minute: Int, val days: List<Int>)

/** Over the hub's `POST /api/v1/flows/<id>/enabled`, `/run` and `/wake-up` routes. */
class HttpFlowsClient(
    private val baseUrl: suspend () -> String?,
    private val post: suspend (url: String, json: String) -> HttpPostResult,
) : FlowsClient {
    override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> =
        postTo(id, "enabled", flowsJson.encodeToString(EnabledBody(enabled)))

    override suspend fun run(id: String): Result<Unit> = postTo(id, "run", "{}")

    override suspend fun setWakeUp(id: String, hour: Int, minute: Int, days: List<Int>): Result<Unit> =
        postTo(id, "wake-up", flowsJson.encodeToString(WakeUpBody(hour, minute, days)))

    private suspend fun postTo(id: String, action: String, body: String): Result<Unit> {
        val base = baseUrl() ?: return Result.failure(IllegalStateException("no hub url"))
        val url = "${base.trimEnd('/')}/api/v1/flows/${encodePathPart(id)}/$action"
        return try {
            when (val r = post(url, body)) {
                is HttpPostResult.Response ->
                    if (r.status in 200..299) Result.success(Unit) else Result.failure(FlowsHttpError(r.status))
                HttpPostResult.NotAllowed -> Result.failure(IllegalStateException("the gateway refused the hub"))
                is HttpPostResult.Unavailable -> Result.failure(IllegalStateException("hub unavailable: ${r.reason}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
