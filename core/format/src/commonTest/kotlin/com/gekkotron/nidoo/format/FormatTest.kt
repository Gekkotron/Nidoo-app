package com.gekkotron.nidoo.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatTest {
    private val en = formatLocale("en-US")
    private val fr = formatLocale("fr-FR")

    @Test fun englishNumbersGroupWithCommas() {
        assertEquals("1,234.5", formatNumber(1234.5, en, 0, 1))
    }

    @Test fun frenchNumbersUseADecimalComma() {
        assertTrue(formatNumber(1234.5, fr, 0, 1) in setOf("1 234,5", "1 234,5", "1 234,5"))
    }

    @Test fun groupingCanBeOff() {
        assertEquals("1234.5", formatNumber(1234.5, en, 1, 1, grouping = false))
    }

    @Test fun fractionDigitsAreBounded() {
        assertEquals("2", formatNumber(2.0, en, 0, 1))
        assertEquals("2.3", formatNumber(2.34, en, 0, 1))
    }

    @Test fun dayMonthFollowsTheLocale() {
        assertEquals("15 janv.", formatEpochSeconds(NOON, "d MMM", fr, "UTC"))
        assertEquals("15 Jan", formatEpochSeconds(NOON, "d MMM", en, "UTC"))
    }

    @Test fun clockIsTwentyFourHour() {
        assertEquals("16:05", formatEpochSeconds(NOON + 3 * 3600 + 5 * 60, "HH:mm", en, "Europe/Paris"))
    }

    @Test fun localHourUsesTheZone() {
        assertEquals(13, localHour(NOON, "Europe/Paris"))
        assertEquals(12, localHour(NOON, "UTC"))
    }

    @Test fun accentsFold() {
        assertEquals("Salle a manger", stripMarks("Salle à manger"))
        assertEquals("Sejour Elodie C", stripMarks("Séjour Élodie Ç"))
    }

    @Test fun halfUpRounding() {
        assertEquals(20.2, roundHalfUp(20.1 + 0.1, 2))
        assertEquals(0.13, roundHalfUp(0.125, 2))
        assertEquals(-0.13, roundHalfUp(-0.125, 2))
        assertEquals(21.0, roundHalfUp(21.0, 2))
    }

    private companion object {
        const val NOON = 1768478400L
    }
}
