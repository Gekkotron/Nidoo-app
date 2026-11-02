package com.gekkotron.nidoo.feature.energy.presentation

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry of the Orbite energy view: two concentric rings of arcs (inner = where power comes from,
 * outer = where it goes) and the dots that drift along them. Pure; a port of the hub's
 * `energy_orbite.rs` (hub repo @ 1226316).
 */
enum class Ring { INNER, OUTER }

data class OrbiteArc(
    /** The [SankeyNode] id. */
    val id: String,
    val ring: Ring,
    val role: SankeyRole,
    /** Consumer colour slot. */
    val slot: Int,
    val name: String?,
    /** Measured, for labels. */
    val watts: Double,
    /** Drawn, from [SankeyNode.size]. */
    val size: Double,
    /** Percent of its own ring's sum, rounded. */
    val share: Int,
    /** Degrees, 0 = top, clockwise, gaps applied. */
    val a0: Double,
    val a1: Double,
    /** Others only: how many consumers it folds. */
    val count: Int,
)

data class Rings(val inner: List<OrbiteArc> = emptyList(), val outer: List<OrbiteArc> = emptyList(), val total: Double = 0.0)

object OrbiteLayout {
    /** Empty angle between two neighbouring arcs, in degrees. */
    const val GAP_DEG = 1.6

    /** Arcs spanning less than this carry no dots. */
    const val MIN_DOT_ARC_DEG = 6.0

    /** An arc is never drawn narrower than this, in degrees. */
    private const val MIN_SPAN_DEG = 0.2

    private val INNER = listOf(SankeyRole.SOLAR, SankeyRole.BATTERY, SankeyRole.GRID)
    private val OUTER = listOf(SankeyRole.EXPORT, SankeyRole.BATTERY_CHARGE, SankeyRole.CONSUMER, SankeyRole.OTHERS, SankeyRole.UNITEMISED)

    /** The two rings of a Sankey; both are scaled to the larger of the two sums so arcs line up. */
    fun rings(s: Sankey): Rings {
        val inner = pick(s, INNER)
        val outer = pick(s, OUTER)
        val total = maxOf(inner.sumOf { it.size }, outer.sumOf { it.size })
        if (total <= 0.0 || !total.isFinite()) return Rings()
        return Rings(layOut(inner, Ring.INNER, total), layOut(outer, Ring.OUTER, total), total)
    }

    /** The nodes of one ring, in drawing order, without the empty ones. */
    private fun pick(s: Sankey, roles: List<SankeyRole>): List<SankeyNode> =
        roles.flatMap { r -> s.nodes.filter { it.role == r } }.filter { it.size > 0.0 }

    private fun layOut(nodes: List<SankeyNode>, ring: Ring, total: Double): List<OrbiteArc> {
        val sum = nodes.sumOf { it.size }
        val singleFull = nodes.size == 1 && nodes[0].size >= total
        var start = 0.0
        return nodes.map { n ->
            val span = n.size / total * 360.0
            val (a0, a1) = when {
                singleFull -> 0.0 to 360.0
                span - GAP_DEG < MIN_SPAN_DEG -> (start + span / 2).let { mid -> mid - MIN_SPAN_DEG / 2 to mid + MIN_SPAN_DEG / 2 }
                else -> start + GAP_DEG / 2 to start + span - GAP_DEG / 2
            }
            start += span
            OrbiteArc(n.id, ring, n.role, n.slot, n.name, n.watts, n.size, (n.size / sum * 100).roundToInt(), a0, minOf(a1, 360.0), n.count)
        }
    }

    /** The point at [deg] (0 = top, clockwise) on the circle of radius [r]. */
    fun point(cx: Double, cy: Double, r: Double, deg: Double): Pair<Double, Double> {
        val a = Math.toRadians(deg)
        return (cx + r * sin(a)) to (cy - r * cos(a))
    }

    /** Degrees per second a dot drifts at: faster for more power. */
    fun dotSpeed(watts: Double): Double = 10.0 + 0.9 * sqrt(maxOf(watts, 0.0))

    /**
     * [phase] (a turn of the arc, 0..1) after [dtS] seconds on [arc]. The phase is kept per arc, so
     * a new reading changes how fast the dots go, never where they are.
     */
    fun advancePhase(phase: Double, dtS: Double, arc: OrbiteArc): Double {
        val span = arc.a1 - arc.a0
        if (span <= 0.0) return phase
        return (phase + dtS.coerceIn(0.0, 0.1) * dotSpeed(arc.watts) / span).mod(1.0)
    }

    /** The dots on an arc at [phase]: (angle in degrees, opacity 0..1). Dots fade in and out at the arc's ends. */
    fun arcDots(arc: OrbiteArc, phase: Double): List<Pair<Double, Double>> {
        val span = arc.a1 - arc.a0
        if (arc.role == SankeyRole.OTHERS || arc.role == SankeyRole.UNITEMISED || span < MIN_DOT_ARC_DEG) return emptyList()
        val n = maxOf((span / 42.0).roundToInt(), 1)
        return (0 until n).map { j ->
            val f = (phase + j.toDouble() / n).mod(1.0)
            val opacity = (minOf(f, 1.0 - f) * span / 6.0).coerceIn(0.0, 1.0)
            (arc.a0 + f * span) to opacity
        }
    }
}
