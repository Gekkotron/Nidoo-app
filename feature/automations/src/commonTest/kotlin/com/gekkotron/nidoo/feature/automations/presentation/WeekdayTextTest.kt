package com.gekkotron.nidoo.feature.automations.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekdayTextTest {
    private val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun text(days: List<Int>, first: Int) = weekdayText(days, first, names::get)

    @Test
    fun weekdaysAreARange() = assertEquals("Mon–Fri", text(listOf(1, 2, 3, 4, 5), 1))

    @Test
    fun weekendFollowsTheFirstDay() {
        assertEquals("Sat, Sun", text(listOf(0, 6), 1))
        assertEquals("Sun, Sat", text(listOf(0, 6), 0))
    }

    @Test
    fun nonConsecutiveDaysAreListed() = assertEquals("Mon, Wed", text(listOf(1, 3), 1))

    @Test
    fun everyDayIsOneRange() = assertEquals("Mon–Sun", text(listOf(0, 1, 2, 3, 4, 5, 6), 1))

    @Test
    fun noDayIsEmpty() = assertEquals("", text(emptyList(), 1))
}
