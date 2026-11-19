package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2

/** Where the robot is and which way it faces; 0 degrees is +x, 90 degrees is +y. */
internal data class RobotPose(val center: Offset, val headingDeg: Float)

/** The cleaning route: back-and-forth rows through the room, as pure geometry on the 24-unit grid. */
internal object RobotPath {
    val ROWS: List<Offset> = listOf(
        Offset(5f, 5.5f), Offset(19f, 5.5f), Offset(19f, 10f), Offset(5f, 10f),
        Offset(5f, 14.5f), Offset(19f, 14.5f), Offset(19f, 19f), Offset(5f, 19f),
    )

    private val CUMULATIVE: List<Float> = run {
        val out = ArrayList<Float>(ROWS.size)
        var sum = 0f
        ROWS.forEachIndexed { i, p ->
            if (i > 0) sum += abs(p.x - ROWS[i - 1].x) + abs(p.y - ROWS[i - 1].y)
            out += sum
        }
        out
    }

    val LENGTH: Float = CUMULATIVE.last()

    /** The pose at [t] of the way along the rows; [t] is clamped to 0..1. */
    fun poseAt(t: Float): RobotPose {
        val d = distance(t)
        val seg = (1 until ROWS.size).firstOrNull { d <= CUMULATIVE[it] } ?: (ROWS.size - 1)
        val from = ROWS[seg - 1]
        val to = ROWS[seg]
        val f = (d - CUMULATIVE[seg - 1]) / (CUMULATIVE[seg] - CUMULATIVE[seg - 1])
        val heading = Math.toDegrees(atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())).toFloat()
        return RobotPose(Offset(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f), heading)
    }

    /** Every corner already passed, then the robot's own position. */
    fun trailTo(t: Float): List<Offset> {
        val d = distance(t)
        return ROWS.filterIndexed { i, _ -> CUMULATIVE[i] < d } + poseAt(t).center
    }

    private fun distance(t: Float): Float = t.coerceIn(0f, 1f) * LENGTH
}

/** Time for one row's worth of travel; the whole round is ten of them. Baseline for any unknown speed. */
internal fun rowPeriodMs(fanSpeed: String?): Int = 10 * when (fanSpeed?.lowercase()) {
    "silent", "silence", "quiet", "eco", "low" -> 1100
    "turbo", "high" -> 600
    "max", "max+", "max_plus" -> 450
    else -> 800
}
