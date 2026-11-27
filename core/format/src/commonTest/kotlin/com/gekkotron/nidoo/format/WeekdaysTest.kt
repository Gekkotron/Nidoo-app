package com.gekkotron.nidoo.format

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekdaysTest {
    private val en = formatLocale("en-US")
    private val fr = formatLocale("fr-FR")

    @Test fun weekStartsByLocale() {
        assertEquals(1, firstDayOfWeek(fr))
        assertEquals(0, firstDayOfWeek(en))
    }

    @Test fun shortNamesFollowTheLocale() {
        assertEquals("Mon", shortWeekdayName(1, en))
        assertEquals("dim.", shortWeekdayName(0, fr))
        assertEquals(7, (0..6).map { shortWeekdayName(it, en) }.toSet().size)
    }
}
