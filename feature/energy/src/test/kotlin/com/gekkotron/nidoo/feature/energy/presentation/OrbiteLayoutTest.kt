package com.gekkotron.nidoo.feature.energy.presentation

import com.gekkotron.nidoo.feature.energy.presentation.OrbiteLayout.GAP_DEG
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ported from the hub's `energy_orbite.rs` tests (Oikos-os 1226316). */
class OrbiteLayoutTest {
    private val wide = 8

    private fun node(id: String, kind: NodeKind, watts: Double, flow: Flow) = EnergyNode(id = id, kind = kind, value = watts, flow = flow)
    private fun consumer(name: String, watts: Double) =
        EnergyNode(id = "consumer:" + name.lowercase(), kind = NodeKind.CONSUMER, name = name, value = watts, flow = Flow.IN)

    private fun build(nodes: List<EnergyNode>, consumers: List<EnergyNode> = emptyList()) =
        EnergySankey.build(EnergyUiState(loading = false, diagram = nodes, cards = consumers, consumers = consumers), wide)

    private fun List<OrbiteArc>.ids() = map { it.id }

    private fun balanced() = build(
        listOf(
            node("solar", NodeKind.SOLAR, 2140.0, Flow.IN),
            node("grid", NodeKind.GRID, 380.0, Flow.IN),
            node("battery", NodeKind.BATTERY, 260.0, Flow.OUT),
            node("house", NodeKind.HOUSE, 2260.0, Flow.IN),
        ),
        listOf(consumer("Heater", 1200.0), consumer("Oven", 540.0), consumer("Fridge", 210.0)),
    )

    @Test
    fun balancedRingsLineUp() {
        val r = OrbiteLayout.rings(balanced())
        assertEquals(listOf("solar", "grid"), r.inner.ids())
        assertEquals("battery-charge", r.outer[0].id)
        assertTrue(r.outer[1].id.startsWith("consumer:"))
        assertEquals("unitemised", r.outer.last().id)
        val end = 360.0 - GAP_DEG / 2
        assertEquals(end, r.inner.last().a1, 0.01)
        assertEquals(end, r.outer.last().a1, 0.01)
    }

    @Test
    fun exportSitsFirst() {
        val s = build(
            listOf(node("solar", NodeKind.SOLAR, 3000.0, Flow.IN), node("grid", NodeKind.GRID, 1200.0, Flow.OUT), node("house", NodeKind.HOUSE, 1800.0, Flow.IN)),
            listOf(consumer("Heater", 900.0)),
        )
        val r = OrbiteLayout.rings(s)
        assertEquals("export", r.outer[0].id)
        assertEquals(SankeyRole.EXPORT, r.outer[0].role)
    }

    @Test
    fun metersDisagreeTheShortRingStopsShort() {
        val s = build(listOf(node("solar", NodeKind.SOLAR, 500.0, Flow.IN), node("house", NodeKind.HOUSE, 2000.0, Flow.IN)), listOf(consumer("Heater", 300.0)))
        val r = OrbiteLayout.rings(s)
        assertTrue(r.inner.last().a1 < 350.0, "${r.inner}")
        assertEquals(360.0 - GAP_DEG / 2, r.outer.last().a1, 0.01)
        assertTrue((r.inner + r.outer).all { it.a1 <= 360.0 })
    }

    @Test
    fun zeroSizeNodesGiveNoArc() {
        val s = build(
            listOf(node("solar", NodeKind.SOLAR, 800.0, Flow.IN), node("grid", NodeKind.GRID, 0.0, Flow.IN), node("house", NodeKind.HOUSE, 800.0, Flow.IN)),
        )
        val r = OrbiteLayout.rings(s)
        assertEquals(listOf("solar"), r.inner.ids())
        assertTrue((r.inner + r.outer).all { it.size > 0.0 })
    }

    @Test
    fun ringsOfEmptySankeyAreEmpty() {
        val r = OrbiteLayout.rings(Sankey(emptyList(), emptyList(), null, false))
        assertTrue(r.inner.isEmpty() && r.outer.isEmpty())
        assertEquals(0.0, r.total)
    }

    @Test
    fun dotsStayInsideTheirArc() {
        val r = OrbiteLayout.rings(balanced())
        var t = 0.0
        while (t < 10.0) {
            for (a in r.inner + r.outer) {
                for ((deg, op) in OrbiteLayout.arcDots(a, t.mod(1.0))) {
                    assertTrue(deg >= a.a0 - 1e-9 && deg <= a.a1 + 1e-9, "$deg in $a")
                    assertTrue(op in 0.0..1.0)
                }
            }
            t += 0.37
        }
        val un = r.outer.first { it.id == "unitemised" }
        assertTrue(OrbiteLayout.arcDots(un, 1.0).isEmpty())
        val short = r.inner[0].copy(a0 = 10.0, a1 = 15.0)
        assertTrue(OrbiteLayout.arcDots(short, 1.0).isEmpty())
    }

    @Test
    fun aNewReadingChangesTheSpeedNotThePlace() {
        val r = OrbiteLayout.rings(balanced())
        val a = r.outer[1]
        val phase = OrbiteLayout.advancePhase(0.0, 0.05, a)
        val busier = a.copy(watts = a.watts * 3, a0 = a.a0 + 2, a1 = a.a1 + 8)
        // Same phase, so the dots stay at the same share of their arc.
        fun frac(arc: OrbiteArc) = OrbiteLayout.arcDots(arc, phase).map { (deg, _) -> (deg - arc.a0) / (arc.a1 - arc.a0) }
        frac(a).zip(frac(busier)).forEach { (x, y) -> assertTrue(abs(x - y) < 1e-9) }
        // More power moves the phase faster; a long pause moves it a bounded step.
        assertTrue(OrbiteLayout.advancePhase(0.0, 0.05, busier) > 0.0)
        assertTrue(OrbiteLayout.advancePhase(0.0, 60.0, a) <= OrbiteLayout.advancePhase(0.0, 0.1, a) + 1e-12)
    }

    @Test
    fun nightGridOnlyFillsBothRings() {
        val s = build(listOf(node("grid", NodeKind.GRID, 300.0, Flow.IN), node("house", NodeKind.HOUSE, 300.0, Flow.IN)))
        val r = OrbiteLayout.rings(s)
        assertEquals(listOf("grid"), r.inner.ids())
        assertEquals(listOf("unitemised"), r.outer.ids())
        for (a in r.inner + r.outer) assertEquals(0.0 to 360.0, a.a0 to a.a1)
    }

    @Test
    fun pointIsClockwiseFromTheTop() {
        val (x, y) = OrbiteLayout.point(100.0, 100.0, 10.0, 90.0)
        assertEquals(110.0, x, 1e-9)
        assertEquals(100.0, y, 1e-9)
    }
}
