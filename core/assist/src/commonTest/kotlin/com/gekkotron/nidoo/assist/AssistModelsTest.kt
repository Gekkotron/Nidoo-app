package com.gekkotron.nidoo.assist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistModelsTest {
    private fun turn(outcome: String, question: String = "null") =
        """{"id":"t1","at":1,"client":"c","text":"allume le salon","simulate":false,"outcome":"$outcome","reply":"Salon allumé.","question":$question,"trace":{"words":[]},"wrong":false}"""

    @Test
    fun `parses an acted turn`() {
        assertEquals(AssistTurn("t1", "allume le salon", AssistOutcome.ACTED, "Salon allumé.", null), AssistJson.parseTurn(turn("acted")))
    }

    @Test
    fun `parses a real hub turn`() {
        val json = """{"id":"55c4a9ab5fd7e504","at":1793819715,"client":"spike","text":"il fait combien dehors ?","simulate":true,"outcome":"answered","reply":"Simulation : Il fait 6,7 °C dehors.","question":null,"trace":{"words":["il","fait","combien","dehors","?"],"intent":"read_temp","room":null,"roomHow":null,"entities":["weather_temperature"],"value":null,"gate":null,"actions":[],"elapsedUs":1438},"wrong":false}"""
        assertEquals(
            AssistTurn("55c4a9ab5fd7e504", "il fait combien dehors ?", AssistOutcome.ANSWERED, "Simulation : Il fait 6,7 °C dehors.", null),
            AssistJson.parseTurn(json),
        )
    }

    @Test
    fun `parses a choice question`() {
        val q = """{"pending":"p1","kind":"choice","choices":["Chambre Léa","Chambre parentale"]}"""
        assertEquals(
            AssistQuestion("p1", QuestionKind.CHOICE, listOf("Chambre Léa", "Chambre parentale")),
            AssistJson.parseTurn(turn("asked", q))?.question,
        )
    }

    @Test
    fun `parses a confirm question with empty choices`() {
        val q = """{"pending":"p2","kind":"confirm","choices":[]}"""
        val parsed = AssistJson.parseTurn(turn("confirming", q))
        assertEquals(AssistOutcome.CONFIRMING, parsed?.outcome)
        assertEquals(QuestionKind.CONFIRM, parsed?.question?.kind)
        assertTrue(parsed?.question?.choices?.isEmpty() == true)
    }

    @Test
    fun `an unknown outcome is FAILED`() {
        assertEquals(AssistOutcome.FAILED, AssistJson.parseTurn(turn("thinking"))?.outcome)
    }

    @Test
    fun `garbage is null`() {
        assertNull(AssistJson.parseTurn("nope"))
    }

    @Test
    fun `text request`() {
        assertEquals("""{"client":"android-1","text":"éteins"}""", AssistJson.textRequest("android-1", "éteins"))
    }

    @Test
    fun `reply requests`() {
        assertEquals("""{"client":"c","reply":{"type":"choice","pending":"p1","choice":1}}""", AssistJson.replyRequest("c", AssistReply.Choice("p1", 1)))
        assertEquals("""{"client":"c","reply":{"type":"yes","pending":"p1"}}""", AssistJson.replyRequest("c", AssistReply.Yes("p1")))
        assertEquals("""{"client":"c","reply":{"type":"no","pending":"p1"}}""", AssistJson.replyRequest("c", AssistReply.No("p1")))
    }
}
