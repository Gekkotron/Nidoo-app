package com.gekkotron.nidoo.designsystem.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartGeometryTest {
    @Test fun scaleOfAFlatSeriesIsCentred() {
        assertEquals(0.5f, valueScale(listOf(5.0, 5.0)).fraction(5.0))
    }

    @Test fun scaleHasHeadroom() {
        assertEquals(ValueScale(-5.0, 105.0), valueScale(listOf(0.0, 100.0)))
    }

    @Test fun axisTicksSpanTheScale() {
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), axisTicks(ValueScale(0.0, 30.0)))
    }

    @Test fun timeTicksSpanTheData() {
        assertEquals(listOf(0L, 100L, 200L, 300L), timeTicks(0, 300))
    }

    @Test fun nearestIndexPicksTheClosestSample() {
        val times = listOf(0L, 10L, 20L)
        assertEquals(1, nearestIndex(times, 14.0))
        assertEquals(2, nearestIndex(times, 16.0))
        assertEquals(0, nearestIndex(times, -5.0))
    }

    @Test fun fewerThanTwoPointsDrawNothing() {
        assertFalse(canDrawLine(0))
        assertFalse(canDrawLine(1))
        assertTrue(canDrawLine(2))
    }

    @Test fun plotFractionMapsThePlotRectToZeroAndOne() {
        assertEquals(0f, plotFraction(40f, 40f, 140f))
        assertEquals(1f, plotFraction(140f, 40f, 140f))
        assertEquals(0.5f, plotFraction(90f, 40f, 140f))
        assertEquals(0f, plotFraction(10f, 40f, 140f)) // in the gutter
        assertEquals(1f, plotFraction(200f, 40f, 140f))
    }
}
