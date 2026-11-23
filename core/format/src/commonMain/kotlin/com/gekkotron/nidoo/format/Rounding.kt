package com.gekkotron.nidoo.format

import kotlin.math.floor
import kotlin.math.pow

/** Rounds half away from zero at [decimals] digits (unlike half-even formatting). */
fun roundHalfUp(value: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    val scaled = value * factor
    val rounded = if (scaled >= 0) floor(scaled + 0.5) else -floor(-scaled + 0.5)
    return rounded / factor
}
