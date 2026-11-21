package com.gekkotron.nidoo.transport

import io.github.gekkotron.athena.gateway.client.AthenaGatewayClient
import io.github.gekkotron.athena.gateway.client.GatewayDownload
import io.github.gekkotron.athena.gateway.client.GatewayException
import io.github.gekkotron.athena.gateway.client.GatewayResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** [GatewayDownloader] over the SDK's `download` and `http`, with non-2xx answers returned instead of thrown. */
class AthenaGatewayDownloader internal constructor(
    private val fetch: suspend (url: String, timeoutSeconds: Int, headers: Map<String, String>) -> GatewayDownload,
    private val send: suspend (url: String, body: JsonElement, timeoutSeconds: Int) -> GatewayResponse,
) : GatewayDownloader {
    /** The platform factories `AthenaGatewayDownloader(spec, …)` build the client. */
    internal constructor(client: AthenaGatewayClient) : this(
        fetch = { url, timeout, headers -> client.download(url, headers = headers, timeoutSeconds = timeout, throwOnUpstreamError = false) },
        send = { url, body, timeout ->
            client.http(url, method = "POST", headers = JSON_HEADERS, body = body, timeoutSeconds = timeout, throwOnUpstreamError = false)
        },
    )

    override suspend fun download(url: String, timeoutSeconds: Int, headers: Map<String, String>): RawDownload =
        guarded { fetch(url, timeoutSeconds, headers).let { RawDownload(it.status, it.bytes, it.contentType) } }

    override suspend fun post(url: String, json: String, timeoutSeconds: Int): RawPost =
        guarded { send(url, Json.parseToJsonElement(json), timeoutSeconds).let { RawPost(it.status, it.headers, if (it.body is JsonNull) "" else it.body.toString()) } }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: GatewayException) {
        throw e.toDownloadFailure()
    }

    private companion object {
        val JSON_HEADERS = mapOf("Content-Type" to "application/json")
    }
}

internal fun GatewayException.toDownloadFailure(): Exception = when {
    this is GatewayException.Rejected && (reason.startsWith("destination not allowed") || reason.startsWith("port not allowed")) ->
        DownloadNotAllowed(reason)
    else -> DownloadFailed(publishText(), this)
}
