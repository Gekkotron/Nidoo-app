package com.gekkotron.oikos.flows

import com.gekkotron.oikos.transport.HttpPostResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpFlowsClientTest {
    private val posts = mutableListOf<Pair<String, String>>()
    private var answer: HttpPostResult = HttpPostResult.Response(204, emptyMap())

    private fun client(base: String? = "http://192.168.1.91:8081", post: (suspend (String, String) -> HttpPostResult)? = null) =
        HttpFlowsClient(baseUrl = { base }, post = post ?: { url, json -> posts += url to json; answer })

    @Test
    fun `enabling posts the json body to the flow's enabled route`(): Unit = runBlocking {
        assertTrue(client().setEnabled("night", true).isSuccess)
        assertEquals("http://192.168.1.91:8081/api/v1/flows/night/enabled" to """{"enabled":true}""", posts.single())
    }

    @Test
    fun `disabling posts enabled false`(): Unit = runBlocking {
        assertTrue(client().setEnabled("night", false).isSuccess)
        assertEquals("""{"enabled":false}""", posts.single().second)
    }

    @Test
    fun `flow id is url encoded and base url trailing slash is not doubled`(): Unit = runBlocking {
        client("http://192.168.1.91:8081/").setEnabled("evening light/dim", true)
        assertEquals("http://192.168.1.91:8081/api/v1/flows/evening%20light%2Fdim/enabled", posts.single().first)
    }

    @Test
    fun `a non 2xx answer is a FlowsHttpError carrying the status`(): Unit = runBlocking {
        answer = HttpPostResult.Response(404, emptyMap())
        val e = assertIs<FlowsHttpError>(client().setEnabled("night", true).exceptionOrNull())
        assertEquals(404, e.status)
    }

    @Test
    fun `not allowed and unavailable are failures`(): Unit = runBlocking {
        answer = HttpPostResult.NotAllowed
        assertTrue(client().setEnabled("night", true).isFailure)
        answer = HttpPostResult.Unavailable("no route")
        assertTrue(client().setEnabled("night", true).isFailure)
    }

    @Test
    fun `no base url fails without posting`(): Unit = runBlocking {
        assertTrue(client(base = null).setEnabled("night", true).isFailure)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `a thrown io exception is a failure`(): Unit = runBlocking {
        val r = client(post = { _, _ -> throw IOException("boom") }).setEnabled("night", true)
        assertIs<IOException>(r.exceptionOrNull())
    }

    @Test
    fun `cancellation propagates`() {
        assertFailsWith<CancellationException> {
            runBlocking { client(post = { _, _ -> throw CancellationException("stop") }).setEnabled("night", true) }
        }
    }

    @Test
    fun `run posts to the run route`(): Unit = runBlocking {
        answer = HttpPostResult.Response(202, emptyMap())
        assertTrue(client("http://hub:8090").run("a b").isSuccess)
        assertEquals("http://hub:8090/api/v1/flows/a%20b/run" to "{}", posts.single())
    }

    @Test
    fun `run maps 202 to success and 422 to FlowsHttpError`(): Unit = runBlocking {
        answer = HttpPostResult.Response(202, emptyMap())
        assertTrue(client().run("a").isSuccess)
        answer = HttpPostResult.Response(422, emptyMap())
        assertEquals(422, (client().run("a").exceptionOrNull() as FlowsHttpError).status)
        answer = HttpPostResult.NotAllowed
        assertTrue(client().run("a").isFailure)
        answer = HttpPostResult.Unavailable("x")
        assertTrue(client().run("a").isFailure)
    }
}
