package com.gekkotron.nidoo.transport

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The iOS LAN/gateway HTTP path ([KtorLanOrGatewayGet]); Android's OkHttp one is LanOrGatewayGetTest. */
class LanOrGatewayGetMockEngineTest {
    private class FakeGateway(
        val answer: RawDownload = RawDownload(200, "from gateway".encodeToByteArray(), "text/plain"),
        val refuse: Boolean = false,
    ) : GatewayDownloader {
        val downloads = mutableListOf<Pair<String, Map<String, String>>>()
        val posts = mutableListOf<String>()

        override suspend fun download(url: String, timeoutSeconds: Int, headers: Map<String, String>): RawDownload {
            downloads += url to headers
            if (refuse) throw DownloadNotAllowed("destination not allowed for this secret")
            return answer
        }

        override suspend fun post(url: String, json: String, timeoutSeconds: Int): RawPost {
            posts += json
            return RawPost(201, mapOf("x-via" to "gateway"), "gw")
        }
    }

    private class Probe(val up: Boolean) : LocalProbe {
        override suspend fun reachable() = up
    }

    private val logs = mutableListOf<String>()

    private fun http(engine: MockEngine, local: Boolean? = true, gateway: GatewayDownloader? = FakeGateway(), lanHost: Boolean = true) =
        KtorLanOrGatewayGet(
            routes = { HttpRoute(local?.let(::Probe), gateway) },
            lan = lanClients(engine),
            isLanHost = { lanHost },
            log = { logs += it },
        )

    private fun engine(handler: MockRequestHandler) = MockEngine(handler)

    private val url = "http://192.168.1.91:8090/v1/series"

    @Test
    fun theLanAnswersFirst() = runTest {
        val gateway = FakeGateway()
        val result = http(engine { respond("lan", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain")) }, gateway = gateway).get(url, 1_000)
        assertIs<HttpGetResult.Response>(result)
        assertEquals("lan", result.bytes.decodeToString())
        assertEquals("text/plain", result.contentType)
        assertEquals(emptyList(), gateway.downloads)
    }

    @Test
    fun aLan404IsAResponseWithoutFallback() = runTest {
        val gateway = FakeGateway()
        val result = http(engine { respond("nope", HttpStatusCode.NotFound) }, gateway = gateway).get(url, 1_000)
        assertIs<HttpGetResult.Response>(result)
        assertEquals(404, result.status)
        assertEquals(0, result.bytes.size)
        assertEquals(emptyList(), gateway.downloads)
    }

    @Test
    fun lanGetFallsBackToGatewayWhenProbeFails() = runTest {
        val gateway = FakeGateway()
        val engine = engine { error("must not be called") }
        val result = http(engine, local = false, gateway = gateway).get(url, 1_000, headers = mapOf("Authorization" to "Bearer t"))
        assertIs<HttpGetResult.Response>(result)
        assertEquals("from gateway", result.bytes.decodeToString())
        assertEquals(listOf(url to mapOf("Authorization" to "Bearer t")), gateway.downloads)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun aConnectionErrorFallsBackToTheGateway() = runTest {
        val gateway = FakeGateway()
        val result = http(engine { throw IllegalStateException("NSURLErrorDomain -1004") }, gateway = gateway).get(url, 1_000)
        assertEquals("from gateway", (result as HttpGetResult.Response).bytes.decodeToString())
    }

    @Test
    fun aHostOutsideThePrivateNetworkNeverGetsADirectRequest() = runTest {
        val engine = engine { respond("lan") }
        http(engine, lanHost = false).get(url, 1_000)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun lanRedirectIsNotFollowed() = runTest {
        val engine = engine { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://evil.example/")) }
        val result = http(engine).get(url, 1_000)
        assertEquals(302, (result as HttpGetResult.Response).status)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun bodyOverCapIsRejected() = runTest {
        assertEquals(HttpGetResult.TooLarge, http(engine { respond("12345678901") }).get(url, 10))
        val big = "x".repeat(256 * 1024 + 1)
        val post = http(engine { respond(big, HttpStatusCode.OK) }).post(url, "{}")
        assertIs<HttpPostResult.Response>(post)
        assertEquals(200, post.status)
        assertEquals("", post.body)
    }

    @Test
    fun aRefusedDestinationIsNotAllowedAndLoggedOncePerHost() = runTest {
        val h = http(engine { error("unused") }, local = false, gateway = FakeGateway(refuse = true))
        assertEquals(HttpGetResult.NotAllowed, h.get(url, 1_000))
        assertEquals(HttpGetResult.NotAllowed, h.get(url, 1_000))
        assertEquals(1, logs.size)
    }

    @Test
    fun notAnHttpUrlOrNoRouteIsUnavailable() = runTest {
        val h = http(engine { error("unused") }, local = null, gateway = null)
        assertIs<HttpGetResult.Unavailable>(h.get("ftp://192.168.1.91/x", 1_000))
        assertIs<HttpGetResult.Unavailable>(h.get(url, 1_000))
    }

    @Test
    fun aLanPostSendsJsonAndReturnsLowerCaseHeaders() = runTest {
        val engine = engine { request ->
            assertEquals("""{"user":"u"}""", request.body.toByteArray().decodeToString())
            assertTrue(request.body.contentType.toString().startsWith("application/json"))
            respond(
                "ok",
                HttpStatusCode.OK,
                headers {
                    append("Set-Cookie", "a=1")
                    append("Set-Cookie", "b=2")
                    append("X-Thing", "y")
                },
            )
        }
        val result = http(engine).post(url, """{"user":"u"}""")
        assertIs<HttpPostResult.Response>(result)
        assertEquals("a=1\nb=2", result.headers["set-cookie"])
        assertEquals("y", result.headers["x-thing"])
        assertEquals("ok", result.body)
    }

    @Test
    fun noResendPostIsSentOnce() = runTest {
        val gateway = FakeGateway()
        var calls = 0 // MockEngine's requestHistory skips a handler that throws
        val engine = engine {
            calls++
            throw IllegalStateException("connection reset after the request was sent")
        }
        val result = http(engine, gateway = gateway).post(url, "{}", resendViaGateway = false)
        assertIs<HttpPostResult.Unavailable>(result)
        assertEquals(1, calls)
        assertEquals(emptyList(), gateway.posts)
    }

    @Test
    fun aPostThatNeverReachedTheHubGoesThroughTheGateway() = runTest {
        val gateway = FakeGateway()
        val result = http(engine { throw IllegalStateException("NSURLErrorDomain -1004") }, gateway = gateway)
            .post(url, "{}", resendViaGateway = false)
        assertEquals(201, (result as HttpPostResult.Response).status)
        assertEquals(listOf("{}"), gateway.posts)
    }

    @Test
    fun headersGoOnTheLanRequest() = runTest {
        val engine = engine { respond("ok") }
        http(engine).get(url, 1_000, headers = mapOf("Authorization" to "Bearer t"))
        assertEquals("Bearer t", engine.requestHistory.single().headers["Authorization"])
    }
}
