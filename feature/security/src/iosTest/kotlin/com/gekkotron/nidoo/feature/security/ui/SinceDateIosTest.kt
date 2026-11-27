package com.gekkotron.nidoo.feature.security.ui

import com.gekkotron.nidoo.format.formatLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.time.Instant

class SinceDateIosTest {
    private val fr = formatLocale("fr_FR")
    private val paris = "Europe/Paris"
    private val now = Instant.parse("2025-01-15T10:00:00Z") // a Wednesday

    @Test
    fun sinceDateFollowsTheLocale() {
        assertEquals("10:00", sinceDate(Instant.parse("2025-01-15T09:00:00Z"), now, fr, paris))
        val monday = sinceDate(Instant.parse("2025-01-13T10:00:00Z"), now, fr, paris)
        assertTrue(monday.startsWith("lun."), monday)
        assertTrue(monday.endsWith("11:00"), monday)
        val older = sinceDate(Instant.parse("2025-01-01T10:00:00Z"), now, fr, paris)
        assertContains(older, "1 janv.")
        assertContains(older, "11:00")
    }

    @Test
    fun aFutureDayKeepsItsWeekday() {
        val tomorrow = sinceDate(Instant.parse("2025-01-16T10:00:00Z"), now, fr, paris)
        assertTrue(tomorrow.startsWith("jeu."), tomorrow)
        assertTrue(tomorrow.endsWith("11:00"), tomorrow)
    }
}
