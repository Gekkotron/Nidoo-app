package com.gekkotron.nidoo.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowDtoParseTest {
    @Test
    fun `parses minimal flow with defaults`() {
        val dto = flowsJson.decodeFromString<FlowDto>("""{"id":"a","name":"Morning"}""")
        assertEquals(FlowDto(id = "a", name = "Morning", area = null, enabled = true), dto)
    }

    @Test
    fun `parses full flow and ignores unknown fields`() {
        val dto = flowsJson.decodeFromString<FlowDto>(
            """{"id":"b","name":"Night","area":"bedroom","enabled":false,"description":"x","mode":"single","nodes":[],"edges":[]}""",
        )
        assertEquals(FlowDto("b", "Night", "bedroom", false), dto)
    }

    private fun decode(wakeUp: String, labels: String = """["wake-up"]""") =
        flowsJson.decodeFromString<FlowDto>("""{"id":"w","name":"Alarm","labels":$labels,"wake_up":$wakeUp}""")

    @Test
    fun `reads a wake-up alarm and ignores its other fields`() {
        val dto = decode(
            """{"version":1,"hour":7,"minute":5,"days":[1,2,3,4,5],"player":"p","playlist":"favorites"}""",
        )
        assertTrue(dto.isWakeUp)
        assertEquals(WakeUpTime(7, 5, listOf(1, 2, 3, 4, 5)), dto.wakeUpTime())
    }

    @Test
    fun `a malformed wake_up still decodes but has no time`() {
        listOf(
            "\"x\"",
            """{"hour":25,"minute":0,"days":[1]}""",
            """{"hour":7,"minute":0,"days":[]}""",
        ).forEach { raw ->
            val dto = decode(raw)
            assertTrue(dto.isWakeUp, raw)
            assertNull(dto.wakeUpTime(), raw)
        }
    }

    @Test
    fun `a flow with neither label nor wake_up is not an alarm`() {
        val dto = flowsJson.decodeFromString<FlowDto>("""{"id":"a","name":"Morning"}""")
        assertFalse(dto.isWakeUp)
        assertNull(dto.wakeUpTime())
    }

    @Test
    fun `days are sorted and deduplicated`() {
        assertEquals(listOf(1, 5), decode("""{"hour":7,"minute":0,"days":[5,1,1]}""").wakeUpTime()?.days)
    }
}
