package com.gekkotron.nidoo.dashboards

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutTest {
    private fun w(id: String, span: WidgetSpan, pos: Pair<Int, Int>? = null) =
        Widget(id, WidgetKind.SENSOR, span, WidgetConfig(), pos?.let { Cell(it.first, it.second) })

    private fun cells(vararg ws: Widget) = DashboardLayout.cells(ws.toList()).map { it.row to it.col }

    @Test
    fun `unpositioned widgets flow like the css grid`() {
        assertEquals(
            listOf(0 to 0, 0 to 2, 1 to 2, 2 to 0),
            cells(w("a", WidgetSpan.S2X2), w("b", WidgetSpan.S1X1), w("c", WidgetSpan.S4X2), w("d", WidgetSpan.S1X1)),
        )
    }

    @Test
    fun `positioned widgets keep their cell and leave gaps`() {
        assertEquals(
            listOf(0 to 0, 1 to 0, 0 to 1),
            cells(w("ac", WidgetSpan.S1X1, 0 to 0), w("t", WidgetSpan.S1X1, 1 to 0), w("ac2", WidgetSpan.S1X1, 0 to 1)),
        )
    }

    @Test
    fun `a bad position falls back to the flow`() {
        assertEquals(
            listOf(0 to 0, 0 to 1, 0 to 3),
            cells(w("a", WidgetSpan.S1X1, 0 to 0), w("b", WidgetSpan.S2X1, 0 to 0), w("c", WidgetSpan.S2X1, 0 to 5)),
        )
    }

    @Test
    fun `empty rows are dropped`() {
        assertEquals(
            listOf(0 to 0, 2 to 3, 3 to 0),
            cells(w("a", WidgetSpan.S1X2, 0 to 0), w("b", WidgetSpan.S1X1, 4 to 3), w("c", WidgetSpan.S1X1, 7 to 0)),
        )
    }

    @Test
    fun `reading order sorts by row then column`() {
        val ordered = DashboardLayout.readingOrder(
            listOf(w("x", WidgetSpan.S1X1, 1 to 0), w("y", WidgetSpan.S1X1, 0 to 3), w("z", WidgetSpan.S1X1, 0 to 0)),
        )
        assertEquals(listOf("z", "y", "x"), ordered.map { it.id })
    }

    @Test
    fun `reading order is not list order after a drag`() {
        val ordered = DashboardLayout.readingOrder(
            listOf(w("a", WidgetSpan.S1X1, 0 to 1), w("b", WidgetSpan.S1X1, 0 to 0)),
        )
        assertEquals(listOf("b", "a"), ordered.map { it.id })
    }

    @Test
    fun `unknown span lays out as 1x1`() {
        assertEquals(listOf(0 to 0, 0 to 1), cells(w("a", WidgetSpan.UNKNOWN), w("b", WidgetSpan.UNKNOWN)))
    }
}
