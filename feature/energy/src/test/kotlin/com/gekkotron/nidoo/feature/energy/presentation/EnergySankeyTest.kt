package com.gekkotron.nidoo.feature.energy.presentation

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from the hub's `energy_sankey.rs` tests (hub repo @ 1226316), plus the app's own cases. */
class EnergySankeyTest {
    private fun node(id: String, kind: NodeKind, watts: Double, flow: Flow, status: NodeStatus = NodeStatus.LIVE) =
        EnergyNode(id = id, kind = kind, value = watts, flow = flow, status = status)

    private fun consumer(name: String, watts: Double, status: NodeStatus = NodeStatus.LIVE) =
        EnergyNode(id = "consumer:" + name.lowercase().replace(' ', '-'), kind = NodeKind.CONSUMER, name = name, value = watts, flow = Flow.IN, status = status)

    private fun ui(nodes: List<EnergyNode>, consumers: List<EnergyNode> = emptyList()) =
        EnergyUiState(loading = false, diagram = nodes, cards = consumers, consumers = consumers)

    private fun solar(w: Double) = node("solar", NodeKind.SOLAR, w, Flow.IN)
    private fun grid(w: Double, flow: Flow) = node("grid", NodeKind.GRID, w, flow)
    private fun battery(w: Double, flow: Flow) = node("battery", NodeKind.BATTERY, w, flow)
    private fun house(w: Double) = node("house", NodeKind.HOUSE, w, Flow.IN)

    private fun Sankey.link(from: String, to: String) = links.firstOrNull { it.from == from && it.to == to }?.watts
    private fun Sankey.find(id: String) = nodes.firstOrNull { it.id == id }
    private fun Sankey.consumerCount() = nodes.count { it.role == SankeyRole.CONSUMER }

    private val wide = 8

    @Test
    fun importOnly() {
        val oven = consumer("Oven", 300.0)
        val s = EnergySankey.build(ui(listOf(grid(500.0, Flow.IN), house(500.0)), listOf(oven, consumer("Lamp", 5.0))), wide)
        assertEquals(500.0, s.link("grid", "house"))
        assertEquals(300.0, s.link("house", oven.id))
        assertEquals(200.0, s.link("house", "unitemised"))
        assertNull(s.find("consumer:lamp"))
    }

    @Test
    fun solarSurplusExports() {
        val s = EnergySankey.build(ui(listOf(solar(3000.0), grid(1200.0, Flow.OUT), house(1800.0)), listOf(consumer("Heater", 900.0))), wide)
        assertEquals(1800.0, s.link("solar", "house"))
        assertEquals(1200.0, s.link("solar", "export"))
        val e = s.find("export")!!
        assertEquals(1 to 1200.0, e.column to e.size)
        assertEquals(900.0, s.find("unitemised")!!.watts)
    }

    @Test
    fun chargeFromSolarThenGrid() {
        val s = EnergySankey.build(ui(listOf(solar(300.0), grid(400.0, Flow.IN), battery(500.0, Flow.OUT), house(200.0))), wide)
        assertEquals(300.0, s.link("solar", "battery-charge"))
        assertEquals(200.0, s.link("grid", "battery-charge"))
        assertEquals(200.0, s.link("grid", "house"))
    }

    @Test
    fun batteryFeedsExportWithoutSolar() {
        val s = EnergySankey.build(ui(listOf(battery(800.0, Flow.IN), grid(300.0, Flow.OUT), house(500.0))), wide)
        assertEquals(300.0, s.link("battery", "export"))
        assertEquals(500.0, s.link("battery", "house"))
    }

    @Test
    fun nightWithSolarOffline() {
        val sol = solar(0.0).copy(value = null, status = NodeStatus.UNAVAILABLE)
        val s = EnergySankey.build(ui(listOf(sol, grid(250.0, Flow.IN), house(250.0))), wide)
        assertNull(s.find("solar"))
        assertEquals(250.0, s.link("grid", "house"))
    }

    @Test
    fun houseMeterLowerThanInflow() {
        val s = EnergySankey.build(ui(listOf(grid(1000.0, Flow.IN), house(900.0)), listOf(consumer("Oven", 950.0))), wide)
        val h = s.find("house")!!
        assertEquals(1000.0 to 900.0, h.size to h.watts)
        assertNull(s.find("unitemised"))
    }

    @Test
    fun wideFoldsAfterEight() {
        val cs = (0 until 9).map { consumer("Load $it", 500.0 - 50.0 * it) }
        val s = EnergySankey.build(ui(listOf(grid(5000.0, Flow.IN), house(5000.0)), cs), wide)
        assertEquals(7, s.consumerCount())
        assertEquals(2, s.find("others")!!.count)
        assertTrue(s.folded)
    }

    @Test
    fun narrowFoldsAtFour() {
        val cs = (0 until 12).map { consumer("Load $it", 100.0) }
        val s = EnergySankey.build(ui(listOf(grid(1500.0, Flow.IN), house(1500.0)), cs))
        assertEquals(3, s.consumerCount())
        val o = s.find("others")!!
        assertEquals(9 to 900.0, o.count to o.watts)
        assertEquals(300.0, s.find("unitemised")!!.watts)
    }

    @Test
    fun everyDrawnNodeBalances() {
        val fixtures = listOf(
            ui(listOf(grid(500.0, Flow.IN), house(500.0)), listOf(consumer("Oven", 300.0))),
            ui(listOf(solar(3000.0), grid(1200.0, Flow.OUT), house(1800.0)), listOf(consumer("Heater", 900.0))),
            ui(listOf(solar(300.0), grid(400.0, Flow.IN), battery(500.0, Flow.OUT), house(200.0))),
            ui(listOf(battery(800.0, Flow.IN), grid(300.0, Flow.OUT), house(500.0))),
            ui(listOf(grid(1000.0, Flow.IN), house(900.0)), listOf(consumer("Oven", 950.0))),
        )
        for (u in fixtures) {
            val s = EnergySankey.build(u, wide)
            for (n in s.nodes) {
                val i = s.links.filter { it.to == n.id }.sumOf { it.watts }
                val o = s.links.filter { it.from == n.id }.sumOf { it.watts }
                when (n.column) {
                    1 -> assertTrue(i <= n.size + 1e-9 && o <= n.size + 1e-9, n.id)
                    0 -> assertTrue(o <= n.watts + 1e-9, n.id)
                }
            }
        }
    }

    @Test
    fun nothingKnownIsEmpty() {
        val s = EnergySankey.build(ui(listOf(house(0.0).copy(value = null))), wide)
        assertTrue(s.isEmpty)
        assertNull(s.houseW)
    }

    // The app's own cases (plan Task 3, Review Focus).

    @Test
    fun emptyStateBuildsEmptySankey() {
        val s = EnergySankey.build(EnergyUiState())
        assertTrue(s.isEmpty)
        assertNull(s.houseW)
    }

    @Test
    fun exportTakesSolarFirst() {
        val s = EnergySankey.build(ui(listOf(solar(3000.0), grid(1000.0, Flow.OUT), house(2000.0))))
        assertEquals(2000.0, s.link("solar", "house"))
        assertEquals(1000.0, s.link("solar", "export"))
        assertTrue(s.nodes.all { it.size >= 0.0 })
    }

    @Test
    fun unitemisedNeverNegative() {
        val s = EnergySankey.build(ui(listOf(grid(500.0, Flow.IN), house(500.0)), listOf(consumer("A", 400.0), consumer("B", 300.0))))
        val u = s.find("unitemised")
        assertTrue(u == null || u.watts == 0.0)
        assertTrue(s.links.all { it.watts >= 0.0 })
    }

    @Test
    fun foldsPastFourKeepsThree() {
        val cs = (0 until 6).map { consumer("Load $it", 100.0) }
        val s = EnergySankey.build(ui(listOf(grid(1000.0, Flow.IN), house(1000.0)), cs))
        assertEquals(3, s.consumerCount())
        assertEquals(3, s.find("others")!!.count)
        assertTrue(s.folded)
    }

    @Test
    fun staleConsumerIsLeftOut() {
        val cs = listOf(consumer("A", 100.0), consumer("B", 100.0, NodeStatus.STALE), consumer("C", 100.0))
        val s = EnergySankey.build(ui(listOf(grid(500.0, Flow.IN), house(500.0)), cs))
        assertEquals(2, s.consumerCount())
    }

    @Test
    fun notReportingListsStaleRolesAndConsumers() {
        val off = consumer("AC", 0.0, NodeStatus.UNAVAILABLE)
        val state = ui(
            listOf(solar(800.0), grid(100.0, Flow.IN).copy(status = NodeStatus.STALE), house(900.0), node("water", NodeKind.WATER, 0.0, Flow.IDLE, NodeStatus.STALE)),
            listOf(consumer("Oven", 300.0), off),
        )
        assertEquals(listOf("grid", off.id), state.notReporting().map { it.id })
    }

    @Test
    fun tapTargetMapsSinksToTheirRoleNode() {
        val oven = consumer("Oven", 300.0)
        val state = ui(listOf(solar(800.0), grid(100.0, Flow.OUT), battery(50.0, Flow.OUT), house(650.0)), listOf(oven))
        assertEquals("grid", state.tapTarget("export")?.id)
        assertEquals("battery", state.tapTarget("battery-charge")?.id)
        assertNull(state.tapTarget("others"))
        assertNull(state.tapTarget("unitemised"))
        assertEquals(oven, state.tapTarget(oven.id))
    }
}
