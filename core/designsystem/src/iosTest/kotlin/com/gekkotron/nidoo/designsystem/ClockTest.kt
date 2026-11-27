package com.gekkotron.nidoo.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSLocale

class ClockTest {
    @Test
    fun clockFollowsTheLocale() {
        assertEquals(false, uses24HourClock(NSLocale(localeIdentifier = "en_US")))
        assertEquals(true, uses24HourClock(NSLocale(localeIdentifier = "fr_FR")))
        assertEquals(false, uses24HourClock(NSLocale(localeIdentifier = "zh_TW")))
    }
}
