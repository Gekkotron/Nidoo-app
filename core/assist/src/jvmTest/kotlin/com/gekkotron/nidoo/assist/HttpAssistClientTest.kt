package com.gekkotron.nidoo.assist

import com.gekkotron.nidoo.transport.HttpGetResult
import com.gekkotron.nidoo.transport.HttpPostResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpAssistClientTest {
    private val posts = mutableListOf<Pair<String, String>>()
    private val gets = mutableListOf<Pair<String, Long>>()
    private var postAnswer: HttpPostResult = HttpPostResult.Response(200, emptyMap(), TURN)
    private var getAnswer: HttpGetResult = HttpGetResult.Response(200, byteArrayOf(1, 2, 3), "audio/wav")

    private fun client(base: String? = "http://192.168.1.91:8081/") = HttpAssistClient(
        baseUrl = { base },
        clientId = { "android-x" },
        post = { url, json -> posts += url to json; postAnswer },
        get = { url, max -> gets += url to max; getAnswer },
    )

    private fun response(status: Int, body: String) = HttpPostResult.Response(status, emptyMap(), body)

    @Test
    fun `send posts the text to the assist route`(): Unit = runBlocking {
        val result = client().send("allume le salon")
        assertEquals("http://192.168.1.91:8081/api/v1/assist" to """{"client":"android-x","text":"allume le salon"}""", posts.single())
        assertEquals("t1", assertIs<AssistResult.Ok>(result).turn.id)
    }

    @Test
    fun `answer posts the reply`(): Unit = runBlocking {
        client().answer(AssistReply.Yes("p1"))
        assertEquals("http://192.168.1.91:8081/api/v1/assist", posts.single().first)
        assertTrue(posts.single().second.contains(""""reply":{"type":"yes","pending":"p1"}"""))
    }

    @Test
    fun `no base url is NoHub without a request`(): Unit = runBlocking {
        assertEquals(AssistResult.NoHub, client(base = null).send("x"))
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `NotAllowed is GatewayRefused`(): Unit = runBlocking {
        postAnswer = HttpPostResult.NotAllowed
        assertEquals(AssistResult.GatewayRefused, client().send("x"))
    }

    @Test
    fun `Unavailable is Unreachable`(): Unit = runBlocking {
        postAnswer = HttpPostResult.Unavailable("no route")
        assertEquals(AssistResult.Unreachable, client().send("x"))
    }

    @Test
    fun `422 question expired is QuestionExpired`(): Unit = runBlocking {
        postAnswer = response(422, """{"error":"question expired"}""")
        assertEquals(AssistResult.QuestionExpired, client().answer(AssistReply.No("p1")))
    }

    @Test
    fun `another 422 is Failed`(): Unit = runBlocking {
        postAnswer = response(422, """{"error":"bad reply"}""")
        assertEquals(AssistResult.Failed(422), client().send("x"))
    }

    @Test
    fun `200 with an unparsable body is Failed`(): Unit = runBlocking {
        postAnswer = response(200, "nope")
        assertEquals(AssistResult.Failed(200), client().send("x"))
    }

    @Test
    fun `speech posts the text then downloads the url`(): Unit = runBlocking {
        postAnswer = response(201, """{"id":"dbdc144666d085877cb523867c9393f2","url":"/api/v1/assist/speech/dbdc144666d085877cb523867c9393f2.wav"}""")
        val bytes = client().speech("Salon allumé.")
        assertEquals("http://192.168.1.91:8081/api/v1/assist/speech" to """{"text":"Salon allumé."}""", posts.single())
        assertEquals("http://192.168.1.91:8081/api/v1/assist/speech/dbdc144666d085877cb523867c9393f2.wav" to MAX_WAV_BYTES, gets.single())
        assertContentEquals(byteArrayOf(1, 2, 3), bytes)
    }

    @Test
    fun `speech returns null for 409 and 503`(): Unit = runBlocking {
        postAnswer = response(409, """{"error":"busy"}""")
        assertNull(client().speech("a"))
        postAnswer = response(503, """{"error":"no voice"}""")
        assertNull(client().speech("a"))
        assertTrue(gets.isEmpty())
    }

    @Test
    fun `speech returns null for a failed download or TooLarge`(): Unit = runBlocking {
        postAnswer = response(201, """{"id":"s1","url":"/api/v1/assist/speech/s1.wav"}""")
        getAnswer = HttpGetResult.Response(404, ByteArray(0), null)
        assertNull(client().speech("a"))
        getAnswer = HttpGetResult.TooLarge
        assertNull(client().speech("a"))
        getAnswer = HttpGetResult.Unavailable("x")
        assertNull(client().speech("a"))
    }

    @Test
    fun `speech only downloads the hub's own speech path`(): Unit = runBlocking {
        for (url in listOf("http://evil.example/x.wav", "//evil.example/x.wav", "/api/v1/entities", "/api/v1/assist/speech/../../x")) {
            postAnswer = response(201, """{"id":"s1","url":"$url"}""")
            assertNull(client().speech("a"))
        }
        assertTrue(gets.isEmpty())
    }

    @Test
    fun `speech never posts a blank or too long text`(): Unit = runBlocking {
        assertNull(client().speech("  "))
        assertNull(client().speech("a".repeat(MAX_SPEECH_CHARS + 1)))
        assertTrue(posts.isEmpty())
    }

    private companion object {
        const val TURN = """{"id":"t1","at":1,"client":"c","text":"x","simulate":false,"outcome":"acted","reply":"ok","question":null,"wrong":false}"""
    }
}
