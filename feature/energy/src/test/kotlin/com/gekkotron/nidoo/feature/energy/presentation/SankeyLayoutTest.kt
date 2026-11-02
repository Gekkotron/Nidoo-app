package com.gekkotron.nidoo.feature.energy.presentation

import com.gekkotron.nidoo.feature.energy.presentation.Frame.Companion.NARROW
import com.gekkotron.nidoo.feature.energy.presentation.Frame.Companion.WIDE
import com.gekkotron.nidoo.feature.energy.presentation.SankeyLayout.BAR
import com.gekkotron.nidoo.feature.energy.presentation.SankeyLayout.LABEL_HALF
import com.gekkotron.nidoo.feature.energy.presentation.SankeyLayout.LINE
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ported from the hub's `sankey_layout.rs` tests (Oikos-os 1226316). */
class SankeyLayoutTest {
    private fun node(id: String, role: SankeyRole, column: Int, size: Double) = SankeyNode(id, role, column, null, size, size)
    private fun link(from: String, to: String, watts: Double) = SankeyLink(from, to, watts)
    private fun sankey(nodes: List<SankeyNode>, links: List<SankeyLink>) = Sankey(nodes, links, null, false)

    /** Solar and grid feed the house, which feeds an export sink and two consumers. */
    private fun sample() = sankey(
        listOf(
            node("solar", SankeyRole.SOLAR, 0, 3000.0),
            node("grid", SankeyRole.GRID, 0, 1000.0),
            node("house", SankeyRole.HOUSE, 1, 3500.0),
            node("export", SankeyRole.EXPORT, 1, 500.0),
            node("oven", SankeyRole.CONSUMER, 2, 2000.0),
            node("heat-pump", SankeyRole.CONSUMER, 2, 1500.0),
        ),
        listOf(
            link("solar", "house", 2500.0),
            link("solar", "export", 500.0),
            link("grid", "house", 1000.0),
            link("house", "oven", 2000.0),
            link("house", "heat-pump", 1500.0),
        ),
    )

    private fun Placed.bar(id: String) = bars.first { it.id == id }
    private fun Placed.label(id: String) = labels.first { it.id == id }
    private val frames = listOf(WIDE, NARROW)

    @Test
    fun barsFitTheFrame() {
        for (f in frames) {
            val p = SankeyLayout.place(sample(), f)
            assertEquals(6, p.bars.size)
            assertEquals(5, p.bands.size)
            for (b in p.bars) {
                assertTrue(b.x >= 0 && b.y >= 0, "$b")
                assertTrue(b.x + b.w <= f.w && b.y + b.h <= f.h, "$b")
            }
        }
    }

    @Test
    fun bandsNeverOutgrowTheirBar() {
        for (f in frames) {
            val p = SankeyLayout.place(sample(), f)
            // A band's thickness along the cross axis, at either end.
            fun thickness(b: SankeyBand) = if (f.vertical) abs(b.a1.x - b.a0.x) else abs(b.a1.y - b.a0.y)
            for (bar in p.bars) {
                val len = if (f.vertical) bar.w else bar.h
                val out = p.bands.filter { it.from == bar.id }.sumOf { thickness(it) }
                val inn = p.bands.filter { it.to == bar.id }.sumOf { thickness(it) }
                assertTrue(out <= len + 0.5, "${bar.id} leaves $out > $len")
                assertTrue(inn <= len + 0.5, "${bar.id} enters $inn > $len")
            }
        }
    }

    @Test
    fun bandEndsSitOnTheirBars() {
        for (f in frames) {
            val p = SankeyLayout.place(sample(), f)
            for (b in p.bands) {
                val from = p.bar(b.from)
                val to = p.bar(b.to)
                if (f.vertical) {
                    assertEquals(from.y + BAR, b.a0.y, 1e-9)
                    assertEquals(to.y, b.b0.y, 1e-9)
                } else {
                    assertEquals(from.x + BAR, b.a0.x, 1e-9)
                    assertEquals(to.x, b.b0.x, 1e-9)
                }
            }
        }
    }

    @Test
    fun houseMixFollowsTheBandsIntoIt() {
        for (f in frames) {
            val mix = SankeyLayout.place(sample(), f).houseMix
            // 2500 W of solar then 1000 W of grid fill the 3500 W house bar.
            assertEquals(2, mix.size)
            assertEquals("solar" to 0.0, mix[0].from to mix[0].start)
            assertEquals(2500.0 / 3500.0, mix[0].end, 1e-9)
            assertEquals("grid", mix[1].from)
            assertEquals(mix[0].end, mix[1].start, 1e-9)
            assertEquals(1.0, mix[1].end, 1e-9)
        }
    }

    @Test
    fun houseMixLeavesTheUnsourcedRestOpen() {
        val s = sample().let { it.copy(nodes = it.nodes.map { n -> if (n.id == "house") n.copy(size = 5000.0) else n }) }
        assertEquals(3500.0 / 5000.0, SankeyLayout.place(s, WIDE).houseMix.last().end, 1e-9)
    }

    @Test
    fun columnsSitOnTheMainAxis() {
        var p = SankeyLayout.place(sample(), WIDE)
        assertEquals(p.bar("solar").x, p.bar("grid").x)
        p = SankeyLayout.place(sample(), NARROW)
        assertEquals(p.bar("solar").y, p.bar("grid").y)
        assertEquals(p.bar("oven").y, p.bar("heat-pump").y)
    }

    @Test
    fun verticalSwapsAxes() {
        val n = SankeyLayout.place(sample(), NARROW).bar("house")
        assertTrue(n.w > n.h, "$n")
        val w = SankeyLayout.place(sample(), WIDE).bar("house")
        assertTrue(w.h > w.w, "$w")
    }

    @Test
    fun labelsFollowTheSideRules() {
        var p = SankeyLayout.place(sample(), WIDE)
        assertEquals(Anchor.END, p.label("solar").anchor)
        assertTrue(p.label("solar").x < p.bar("solar").x)
        assertEquals(Anchor.START, p.label("oven").anchor)
        assertTrue(p.label("oven").x > p.bar("oven").x + BAR)
        assertEquals(Anchor.MIDDLE, p.label("house").anchor)
        assertTrue(p.label("house").y < p.bar("house").y)
        assertEquals(Anchor.START, p.label("export").anchor)

        p = SankeyLayout.place(sample(), NARROW)
        assertEquals(Anchor.MIDDLE, p.label("oven").anchor)
        assertEquals(Anchor.MIDDLE, p.label("house").anchor)
        assertTrue(p.label("oven").y > p.bar("oven").y + p.bar("oven").h)
        assertTrue(p.label("house").y < p.bar("house").y)
        // Sources read above their bar, clear of the bands below it.
        for (id in listOf("solar", "grid")) assertTrue(p.label(id).y + LINE < p.bar(id).y, id)
        assertTrue(p.label("export").y > p.bar("export").y + BAR)
        assertTrue(p.label("solar").y > 0.0)
    }

    private fun span(l: SankeyLabel) = when (l.anchor) {
        Anchor.START -> l.x to l.x + 2 * LABEL_HALF
        Anchor.MIDDLE -> l.x - LABEL_HALF to l.x + LABEL_HALF
        Anchor.END -> l.x - 2 * LABEL_HALF to l.x
    }

    @Test
    fun narrowLabelsStayInsideTheFrame() {
        // A tiny consumer first in its column, one last, a big one between: the tiny ones sit at the edges.
        val s = sankey(
            listOf(
                node("house", SankeyRole.HOUSE, 1, 1000.0),
                node("tiny", SankeyRole.CONSUMER, 2, 2.0),
                node("oven", SankeyRole.CONSUMER, 2, 1000.0),
                node("tiny2", SankeyRole.CONSUMER, 2, 2.0),
            ),
            listOf(link("house", "tiny", 2.0), link("house", "oven", 1000.0), link("house", "tiny2", 2.0)),
        )
        val p = SankeyLayout.place(s, NARROW)
        for (l in p.labels) {
            val (from, to) = span(l)
            assertTrue(from >= 0.0 && to <= NARROW.w, "${l.id} $from..$to")
        }
        assertEquals(Anchor.START, p.label("tiny").anchor)
        assertEquals(Anchor.END, p.label("tiny2").anchor)
    }

    @Test
    fun narrowLabelsInARowNeverOverlap() {
        // One big consumer and three thin ones: the thin bars sit closer than a label is wide.
        val s = sankey(
            listOf(
                node("grid", SankeyRole.GRID, 0, 3120.0),
                node("house", SankeyRole.HOUSE, 1, 3120.0),
                node("oven", SankeyRole.CONSUMER, 2, 3000.0),
                node("lamp", SankeyRole.CONSUMER, 2, 40.0),
                node("radio", SankeyRole.CONSUMER, 2, 40.0),
                node("fan", SankeyRole.CONSUMER, 2, 40.0),
            ),
            listOf(
                link("grid", "house", 3120.0),
                link("house", "oven", 3000.0),
                link("house", "lamp", 40.0),
                link("house", "radio", 40.0),
                link("house", "fan", 40.0),
            ),
        )
        val p = SankeyLayout.place(s, NARROW)
        for (a in p.labels) {
            assertTrue(a.y - LINE >= 0.0 && a.y + LINE <= NARROW.h, "${a.id} ${a.y}")
            for (b in p.labels) {
                if (a.id >= b.id || abs(a.y - b.y) >= 2 * LINE) continue
                val (a0, a1) = span(a)
                val (b0, b1) = span(b)
                assertTrue(a1 <= b0 || b1 <= a0, "${a.id} overlaps ${b.id}")
            }
            assertTrue(a.y > p.bar(a.id).y || a.id == "house" || a.id == "grid")
        }
    }

    @Test
    fun emptyModelPlacesNothing() {
        assertEquals(Placed(), SankeyLayout.place(sankey(emptyList(), emptyList()), WIDE))
    }
}
