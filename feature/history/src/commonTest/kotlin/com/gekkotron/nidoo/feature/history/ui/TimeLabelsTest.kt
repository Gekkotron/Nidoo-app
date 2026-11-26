package com.gekkotron.nidoo.feature.history.ui

import com.gekkotron.nidoo.format.formatLocale
import com.gekkotron.nidoo.history.HistoryRange
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeLabelsTest {
    // 2025-01-15T10:00:00Z, which is 11:00 in Europe/Paris.
    private val instant = 1_736_935_200L
    private val paris = "Europe/Paris"

    @Test
    fun timeLabelsFollowTheLocale() {
        assertEquals("15 janv.", timeFormatter(HistoryRange.D30, formatLocale("fr"), paris)(instant))
        assertEquals("11:00", timeFormatter(HistoryRange.H24, formatLocale("fr"), paris)(instant))
        assertEquals("15 Jan", timeFormatter(HistoryRange.D30, formatLocale("en"), paris)(instant))
    }
}
