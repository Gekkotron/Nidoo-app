package com.gekkotron.nidoo.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

class ReduceMotionTest {
    @Test
    fun oneSharedState() {
        assertSame(ReduceMotion.reduce, ReduceMotion.reduce)
        assertEquals(UIAccessibilityIsReduceMotionEnabled(), ReduceMotion.reduce.value)
    }
}
