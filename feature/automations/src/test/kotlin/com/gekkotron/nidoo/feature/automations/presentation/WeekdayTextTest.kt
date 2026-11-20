package com.gekkotron.nidoo.feature.automations.presentation

import org.junit.Test
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.assertEquals

class WeekdayTextTest {
    private fun text(days: List<Int>, first: DayOfWeek) =
        weekdayText(days, first) { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }

    @Test
    fun `a run of weekdays is a range`() = assertEquals("Mon–Fri", text(listOf(1, 2, 3, 4, 5), DayOfWeek.MONDAY))

    @Test
    fun `the weekend follows the first day of the week`() {
        assertEquals("Sat, Sun", text(listOf(0, 6), DayOfWeek.MONDAY))
        assertEquals("Sun, Sat", text(listOf(0, 6), DayOfWeek.SUNDAY))
    }

    @Test
    fun `non consecutive days are listed`() = assertEquals("Mon, Wed", text(listOf(1, 3), DayOfWeek.MONDAY))

    @Test
    fun `every day is one range`() = assertEquals("Mon–Sun", text(listOf(0, 1, 2, 3, 4, 5, 6), DayOfWeek.MONDAY))

    @Test
    fun `no day is empty`() = assertEquals("", text(emptyList(), DayOfWeek.MONDAY))
}
