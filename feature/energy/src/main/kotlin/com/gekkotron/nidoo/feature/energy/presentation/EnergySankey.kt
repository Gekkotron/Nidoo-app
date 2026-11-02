package com.gekkotron.nidoo.feature.energy.presentation

import com.gekkotron.nidoo.feature.energy.presentation.EnergyFlowBuilder.IDLE_W

/**
 * The live energy flow as a balanced Sankey: nodes in three columns (sources; the house and its
 * sinks; consumers) and the links between them. Pure; Flux and Orbite draw it. A port of the hub's
 * `energy_sankey.rs` (Oikos-os 1226316).
 */
enum class SankeyRole { SOLAR, GRID, BATTERY, HOUSE, EXPORT, BATTERY_CHARGE, CONSUMER, OTHERS, UNITEMISED }

data class SankeyNode(
    /** "solar" "grid" "battery" "house" "export" "battery-charge" "others" "unitemised", or a consumer's [EnergyNode.id]. */
    val id: String,
    val role: SankeyRole,
    /** 0 sources, 1 house + sinks, 2 consumers. */
    val column: Int,
    /** A consumer's name; null for the others (UI words). */
    val name: String?,
    /** What the label shows: the measured value (Others/Unitemised: their sum). */
    val watts: Double,
    /** What the bar is drawn for: max(in, out) of the drawn links, in watts. */
    val size: Double,
    /** Others only: how many consumers it folds. */
    val count: Int = 0,
    /** Consumers only: 0-based position among shown consumers (colour rotation). */
    val slot: Int = 0,
)

data class SankeyLink(val from: String, val to: String, val watts: Double)

data class Sankey(
    /** Column 0 then 1 then 2, top to bottom within a column. */
    val nodes: List<SankeyNode>,
    /** Ordered by source node order, then target node order. */
    val links: List<SankeyLink>,
    val houseW: Double?,
    /** An Others node exists. */
    val folded: Boolean,
) {
    /** No node is big enough to draw. */
    val isEmpty: Boolean get() = nodes.none { it.size >= IDLE_W }
}

object EnergySankey {
    /** A phone folds the consumers when more than this many are powered (hub `FOLD_NARROW`). */
    const val FOLD_PHONE = 4

    fun build(state: EnergyUiState, foldAt: Int = FOLD_PHONE): Sankey {
        val solar = state.node("solar")
        val grid = state.node("grid")
        val battery = state.node("battery")
        val house = state.node("house")

        val sIn = active(solar)
        val gIn = if (grid?.flow == Flow.IN) active(grid) else 0.0
        val bIn = if (battery?.flow == Flow.IN) active(battery) else 0.0
        val export = if (grid?.flow == Flow.OUT) active(grid) else 0.0
        val charge = if (battery?.flow == Flow.OUT) active(battery) else 0.0

        val pool = doubleArrayOf(sIn, gIn, bIn) // solar, grid, battery
        val solarExport = take(pool, 0, export)
        val batteryExport = take(pool, 2, export - solarExport)
        val solarCharge = take(pool, 0, charge)
        val gridCharge = take(pool, 1, charge - solarCharge)
        val (solarHouse, gridHouse, batteryHouse) = Triple(pool[0], pool[1], pool[2])

        val houseMeasured = house?.takeIf { it.status == NodeStatus.LIVE }?.value
        val anyInput = listOfNotNull(solar, grid, battery).any { it.status == NodeStatus.LIVE && it.value != null }
        val houseW = houseMeasured ?: if (anyInput) solarHouse + gridHouse + batteryHouse else null
        val houseVal = houseW ?: 0.0

        var shown = state.consumers.filter { it.status == NodeStatus.LIVE && (it.value ?: -1.0) >= IDLE_W }
        var others: Pair<Double, Int>? = null
        if (shown.size > foldAt) {
            val keep = (foldAt - 1).coerceAtLeast(0)
            val rest = shown.drop(keep)
            shown = shown.take(keep)
            others = rest.sumOf { it.value ?: 0.0 } to rest.size
        }
        val shownSum = shown.sumOf { it.value ?: 0.0 }
        val othersW = others?.first ?: 0.0
        val unitemised = (houseVal - shownSum - othersW).coerceAtLeast(0.0)

        val links = mutableListOf<SankeyLink>()
        fun link(from: String, to: String, watts: Double) {
            if (watts >= IDLE_W) links += SankeyLink(from, to, watts)
        }
        link("solar", "house", solarHouse)
        link("solar", "battery-charge", solarCharge)
        link("solar", "export", solarExport)
        link("grid", "house", gridHouse)
        link("grid", "battery-charge", gridCharge)
        link("battery", "house", batteryHouse)
        link("battery", "export", batteryExport)
        shown.forEach { link("house", it.id, it.value ?: 0.0) }
        link("house", "others", othersW)
        link("house", "unitemised", unitemised)

        fun into(id: String) = links.filter { it.to == id }.sumOf { it.watts }
        fun outOf(id: String) = links.filter { it.from == id }.sumOf { it.watts }

        val nodes = mutableListOf<SankeyNode>()
        // Column 0: sources, sized by what they actually feed.
        for ((id, role, node) in listOf(Triple("solar", SankeyRole.SOLAR, solar), Triple("grid", SankeyRole.GRID, grid), Triple("battery", SankeyRole.BATTERY, battery))) {
            val size = outOf(id)
            if (size >= IDLE_W) nodes += SankeyNode(id, role, 0, null, measured(node), size)
        }
        // Column 1: the house, then the sinks that take from the sources directly.
        val houseSize = maxOf(into("house"), houseVal, outOf("house"))
        if (houseSize >= IDLE_W) nodes += SankeyNode("house", SankeyRole.HOUSE, 1, null, houseVal, houseSize)
        val chargeSize = into("battery-charge")
        if (chargeSize >= IDLE_W) nodes += SankeyNode("battery-charge", SankeyRole.BATTERY_CHARGE, 1, null, charge, chargeSize)
        val exportSize = into("export")
        if (exportSize >= IDLE_W) nodes += SankeyNode("export", SankeyRole.EXPORT, 1, null, export, exportSize)
        // Column 2: consumers, the folded rest, what no consumer accounts for.
        shown.forEachIndexed { slot, c ->
            val w = c.value ?: 0.0
            nodes += SankeyNode(c.id, SankeyRole.CONSUMER, 2, c.name, w, w, slot = slot)
        }
        others?.let { (w, count) -> nodes += SankeyNode("others", SankeyRole.OTHERS, 2, null, w, w, count = count) }
        if (unitemised >= IDLE_W) nodes += SankeyNode("unitemised", SankeyRole.UNITEMISED, 2, null, unitemised, unitemised)

        return Sankey(nodes, links, houseW, others != null)
    }

    /** A reading in watts for allocation: unknown, unavailable or idle counts as 0. */
    private fun active(node: EnergyNode?): Double =
        if (node?.status == NodeStatus.LIVE) node.value?.takeIf { it >= IDLE_W } ?: 0.0 else 0.0

    /** The measured value for display, whatever its size; 0 when unknown or unavailable. */
    private fun measured(node: EnergyNode?): Double = if (node?.status == NodeStatus.LIVE) node.value ?: 0.0 else 0.0

    /** Moves up to [want] out of `pool[i]` and returns how much moved. */
    private fun take(pool: DoubleArray, i: Int, want: Double): Double {
        val t = minOf(pool[i], want).coerceAtLeast(0.0)
        pool[i] -= t
        return t
    }
}

private val ROLE_IDS = listOf("solar", "grid", "battery", "house")

/** The sources, house and consumers that are not reporting, for the caption under Flux and Orbite. Never water. */
fun EnergyUiState.notReporting(): List<EnergyNode> =
    ROLE_IDS.mapNotNull { node(it) }.filter { it.status != NodeStatus.LIVE } +
        consumers.filter { it.status != NodeStatus.LIVE }

/** The node a tap on a Sankey bar, band or arc opens; null for "others" (the consumers sheet) and "unitemised". */
fun EnergyUiState.tapTarget(sankeyId: String): EnergyNode? = when (sankeyId) {
    "export" -> node("grid")
    "battery-charge" -> node("battery")
    "others", "unitemised" -> null
    else -> node(sankeyId) ?: consumers.firstOrNull { it.id == sankeyId }
}
