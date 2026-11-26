package com.gekkotron.nidoo.designsystem.charts

import kotlin.math.abs

/** Maps the values of a series onto 0f (bottom) to 1f (top) of the plot. */
internal data class ValueScale(val low: Double, val high: Double) {
    fun fraction(v: Double): Float = ((v - low) / (high - low)).toFloat()
}

private const val HeadroomShare = 0.05

/** The scale covering [values] with 5% headroom above and below; a flat series is widened by 1 so it sits at 0.5. */
internal fun valueScale(values: List<Double>): ValueScale {
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    if (max - min == 0.0) return ValueScale(min - 1.0, max + 1.0)
    val pad = (max - min) * HeadroomShare
    return ValueScale(min - pad, max + pad)
}

/** [count] evenly spaced values from the scale's low to its high, both included. */
internal fun axisTicks(scale: ValueScale, count: Int = 4): List<Double> {
    if (count < 2) return listOf(scale.low)
    return List(count) { scale.low + (scale.high - scale.low) * it / (count - 1) }
}

/** [count] evenly spaced times from [first] to [last], both included. */
internal fun timeTicks(first: Long, last: Long, count: Int = 4): List<Long> {
    if (count < 2) return listOf(first)
    return List(count) { first + (last - first) * it / (count - 1) }
}

/** The index of the sample whose time is closest to [t]; 0 for an empty list. */
internal fun nearestIndex(times: List<Long>, t: Double): Int {
    var best = 0
    var bestDistance = Double.MAX_VALUE
    times.forEachIndexed { i, time ->
        val distance = abs(time - t)
        if (distance < bestDistance) { best = i; bestDistance = distance }
    }
    return best
}

/** A line needs two samples; with fewer the iOS charts draw only the frame. */
internal fun canDrawLine(count: Int): Boolean = count >= 2

/** Where an x position falls in the plot spanning [left]..[right], as 0..1; a tap in a gutter clamps to the nearest edge. */
internal fun plotFraction(x: Float, left: Float, right: Float): Float =
    if (right <= left) 0f else ((x - left) / (right - left)).coerceIn(0f, 1f)
