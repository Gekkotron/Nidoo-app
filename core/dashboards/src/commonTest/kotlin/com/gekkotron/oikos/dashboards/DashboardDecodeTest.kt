package com.gekkotron.oikos.dashboards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class DashboardDecodeTest {
    @Test
    fun `decodes the spec example`() {
        val payload = """
            {"id":"evening","name":"Soirée","order":1,"show_header":true,
             "widgets":[{"id":"w1","kind":"light","span":"2x1",
               "config":{"entity_id":"light.salon","title":"Salon"},
               "pos":{"row":0,"col":0}}]}
        """.trimIndent()
        assertEquals(
            Dashboard(
                "evening", "Soirée", 1, true,
                listOf(
                    Widget(
                        "w1", WidgetKind.LIGHT, WidgetSpan.S2X1,
                        WidgetConfig(entityId = "light.salon", title = "Salon"), Cell(0, 0),
                    ),
                ),
            ),
            DashboardJson.decode("evening", payload),
        )
    }

    @Test
    fun `topic id wins over payload id`() {
        assertEquals("a", DashboardJson.decode("a", """{"id":"b","name":"N","order":0,"widgets":[]}""").id)
    }

    @Test
    fun `show_header omitted is false`() {
        assertFalse(DashboardJson.decode("a", """{"name":"N","order":0,"widgets":[]}""").showHeader)
    }

    @Test
    fun `missing order sorts last`() {
        assertEquals(Long.MAX_VALUE, DashboardJson.decode("a", """{"name":"N","widgets":[]}""").order)
    }

    private fun one(widget: String) =
        DashboardJson.decode("a", """{"name":"N","order":0,"widgets":[$widget]}""").widgets.single()

    @Test
    fun `history config keeps its range`() {
        val w = one("""{"id":"h","kind":"history_chart","span":"4x2","config":{"entity_id":"t","range":"7d"}}""")
        assertEquals(WidgetConfig(entityId = "t", range = WidgetRange.D7), w.config)
    }

    @Test
    fun `unknown kind span and range decode as UNKNOWN`() {
        val w = one("""{"id":"x","kind":"gauge","span":"3x3","config":{"range":"90d"}}""")
        assertEquals(WidgetKind.UNKNOWN, w.kind)
        assertEquals(WidgetSpan.UNKNOWN, w.span)
        assertEquals(WidgetRange.UNKNOWN, w.config.range)
    }

    @Test
    fun `cameras config`() {
        val w = one("""{"id":"c","kind":"camera","span":"2x2","config":{"cameras":["front","back"],"title":"Cams"}}""")
        assertEquals(listOf("front", "back"), w.config.cameras)
        assertEquals("Cams", w.config.title)
    }

    @Test
    fun `missing name throws`() {
        assertFails { DashboardJson.decode("a", """{"order":0,"widgets":[]}""") }
    }

    @Test
    fun `a malformed widget is dropped, the others survive`() {
        val d = DashboardJson.decode(
            "a",
            """{"name":"N","order":0,"widgets":[42,{"id":"ok","kind":"sensor","span":"1x1","config":{}}]}""",
        )
        assertEquals(listOf("ok"), d.widgets.map { it.id })
    }

    @Test
    fun `a widget without id gets its index, blank title is null, non-object config is empty`() {
        val d = DashboardJson.decode(
            "a",
            """{"name":"N","widgets":[{"kind":"sensor","span":"1x1","config":{"title":"  "}},{"id":"z","kind":"lock","span":"1x1","config":5}]}""",
        )
        assertEquals("#0", d.widgets[0].id)
        assertEquals(null, d.widgets[0].config.title)
        assertEquals(WidgetConfig(), d.widgets[1].config)
    }

    @Test
    fun `unknown top-level keys are ignored`() {
        assertEquals("N", DashboardJson.decode("a", """{"name":"N","order":0,"widgets":[],"future":{"x":1}}""").name)
    }
}
