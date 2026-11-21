package com.gekkotron.nidoo.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/** The two LAN clients: [lan] for GETs and resendable POSTs, [lanOnce] for a POST that must never be sent twice. */
class LanClients(val lan: HttpClient, val lanOnce: HttpClient)

/**
 * LAN clients on [engine] with Android's LAN settings: 5 s connect, 15 s read, no redirects (a LAN host must not
 * bounce the request to another, cleartext host) and no retries. Ktor never resends a request on its own, so
 * [LanClients.lanOnce] is the same client.
 */
fun lanClients(engine: HttpClientEngine): LanClients {
    val client = HttpClient(engine) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = LAN_CONNECT_MILLIS
            socketTimeoutMillis = LAN_READ_MILLIS
        }
    }
    return LanClients(client, client)
}

/**
 * [LanOrGatewayGet] on Ktor, for iOS: a GET or JSON POST of a LAN URL, direct when the LAN is reachable, the
 * gateway's raw download otherwise (30 s). Same results, caps and fallbacks as the OkHttp one Android uses.
 */
class KtorLanOrGatewayGet(
    private val routes: HttpRoutes,
    private val lan: LanClients,
    private val isLanHost: (String) -> Boolean,
    private val log: (String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = SynchronizedObject()
    private val warnedHosts = mutableSetOf<String>() // guarded by [lock]

    private sealed interface Lan {
        class Done(val result: HttpGetResult) : Lan
        class Down(val reason: String) : Lan
    }

    suspend fun get(
        url: String,
        maxBytes: Long,
        onProgress: (read: Long, total: Long?) -> Unit = { _, _ -> },
        headers: Map<String, String> = emptyMap(),
    ): HttpGetResult {
        val host = httpHost(url) ?: return HttpGetResult.Unavailable("not an http url")
        val route = routes.current()
        var lanReason: String? = null
        val probe = route.local
        if (probe != null && isLanHost(host) && probe.reachable()) {
            when (val result = fromLan(url, maxBytes, onProgress, headers)) {
                is Lan.Done -> return result.result
                is Lan.Down -> lanReason = result.reason
            }
        }
        val gateway = route.gateway ?: return HttpGetResult.Unavailable(lanReason ?: "no route")
        return fromGateway(gateway, url, host, maxBytes, headers)
    }

    /** As [LanOrGatewayGet.post]: with [resendViaGateway] false, only a failure to connect falls back to the gateway. */
    suspend fun post(url: String, json: String, resendViaGateway: Boolean = true): HttpPostResult {
        val host = httpHost(url) ?: return HttpPostResult.Unavailable("not an http url")
        val route = routes.current()
        var lanReason: String? = null
        val probe = route.local
        if (probe != null && isLanHost(host) && probe.reachable()) {
            try {
                val client = if (resendViaGateway) lan.lan else lan.lanOnce
                return client.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    setBody(json)
                }.execute { response ->
                    HttpPostResult.Response(response.status.value, response.lowerCaseHeaders(), readPostBody(response))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lanReason = e.message ?: e::class.simpleName ?: "error"
                if (!resendViaGateway && !e.isConnectFailure()) return HttpPostResult.Unavailable(lanReason)
            }
        }
        val gateway = route.gateway ?: return HttpPostResult.Unavailable(lanReason ?: "no route")
        return try {
            gateway.post(url, json, GATEWAY_TIMEOUT_SECONDS).let { HttpPostResult.Response(it.status, it.headers, it.body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadNotAllowed) {
            warnOnce(host, e.reason)
            HttpPostResult.NotAllowed
        } catch (e: Exception) {
            HttpPostResult.Unavailable(e.message ?: e::class.simpleName ?: "error")
        }
    }

    private suspend fun fromGateway(gateway: GatewayDownloader, url: String, host: String, maxBytes: Long, headers: Map<String, String>): HttpGetResult = try {
        val download = gateway.download(url, GATEWAY_TIMEOUT_SECONDS, headers)
        if (download.bytes.size > maxBytes) HttpGetResult.TooLarge else HttpGetResult.Response(download.status, download.bytes, download.contentType)
    } catch (e: CancellationException) {
        throw e
    } catch (e: DownloadNotAllowed) {
        warnOnce(host, e.reason)
        HttpGetResult.NotAllowed
    } catch (e: Exception) {
        HttpGetResult.Unavailable(e.message ?: e::class.simpleName ?: "error")
    }

    private suspend fun fromLan(url: String, maxBytes: Long, onProgress: (Long, Long?) -> Unit, headers: Map<String, String>): Lan = try {
        lan.lan.prepareGet(url) { headers { headers.forEach { (k, v) -> append(k, v) } } }.execute { response ->
            if (!response.status.isSuccess()) Lan.Done(HttpGetResult.Response(response.status.value, ByteArray(0), null))
            else Lan.Done(readBody(response, maxBytes, onProgress))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Lan.Down(e.message ?: e::class.simpleName ?: "error")
    }

    private suspend fun readBody(response: HttpResponse, maxBytes: Long, onProgress: (Long, Long?) -> Unit): HttpGetResult = withContext(io) {
        val total = response.contentLength()?.takeIf { it >= 0 }
        if (total != null && total > maxBytes) return@withContext HttpGetResult.TooLarge
        val bytes = readCapped(response, maxBytes) { read -> onProgress(read, total) } ?: return@withContext HttpGetResult.TooLarge
        HttpGetResult.Response(response.status.value, bytes, response.contentType()?.toString())
    }

    /** The body text; empty when it is over [MAX_POST_BODY]. */
    private suspend fun readPostBody(response: HttpResponse): String = withContext(io) {
        readCapped(response, MAX_POST_BODY.toLong()) {}?.decodeToString() ?: ""
    }

    /** The body, or null as soon as it passes [max] bytes. */
    private suspend fun readCapped(response: HttpResponse, max: Long, onRead: (Long) -> Unit): ByteArray? {
        val channel = response.bodyAsChannel()
        val out = Buffer()
        val chunk = ByteArray(CHUNK)
        var read = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = channel.readAvailable(chunk, 0, chunk.size)
            if (n < 0) break
            if (n == 0) continue
            read += n
            if (read > max) return null
            out.write(chunk, 0, n)
            onRead(read)
        }
        return out.readByteArray()
    }

    private fun warnOnce(host: String, reason: String) {
        val first = synchronized(lock) { warnedHosts.add(host) }
        if (first) log("the gateway refused $host ($reason); add the host to this key's rules")
    }

    private companion object {
        const val GATEWAY_TIMEOUT_SECONDS = 30
        const val CHUNK = 16 * 1024
        const val MAX_POST_BODY = 256 * 1024
    }
}

private const val LAN_CONNECT_MILLIS = 5_000L
private const val LAN_READ_MILLIS = 15_000L

/** The host of an http(s) URL with one, or null. */
private fun httpHost(url: String): String? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val parsed = try {
        Url(url)
    } catch (e: Exception) {
        return null
    }
    return parsed.host.takeIf { it.isNotEmpty() }
}

/** Lower-case names; a repeated header (several Set-Cookie) keeps every value, joined with `\n`. */
private fun HttpResponse.lowerCaseHeaders(): Map<String, String> =
    headers.names().associate { it.lowercase() to headers.getAll(it).orEmpty().joinToString("\n") }

/**
 * The request never left the phone: a connect timeout, or (Darwin) cannot find / connect to the host or no
 * network (NSURLErrorDomain -1003, -1004, -1009), or a refused / unresolved socket.
 */
private fun Exception.isConnectFailure(): Boolean {
    if (this is ConnectTimeoutException) return true
    val text = generateSequence<Throwable>(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    return CONNECT_FAILURE_MARKERS.any { it in text }
}

private val CONNECT_FAILURE_MARKERS = listOf("-1003", "-1004", "-1009", "Connection refused", "UnresolvedAddress", "Unable to resolve host")
