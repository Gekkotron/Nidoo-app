package com.gekkotron.nidoo.feature.home.presentation

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Media player spec, `position_at`: the bar runs from where the player measured it, never backwards. */
class MediaProgressTest {
    private val now = 1_000_000L

    @Test
    fun `waits for a future position_at`() {
        // Measured 2 s from now: the bar holds at the position instead of going back.
        assertEquals(0.25f, mediaProgress(200.0, 50.0, now + 2_000, playing = true, nowMs = now))
    }

    @Test
    fun `stays still while loading`() {
        assertEquals(0.25f, mediaProgress(200.0, 50.0, null, playing = true, nowMs = now))
        assertEquals(0f, mediaProgress(200.0, null, null, playing = true, nowMs = now))
    }

    @Test
    fun `moves while playing`() {
        assertEquals(0.3f, mediaProgress(200.0, 50.0, now - 10_000, playing = true, nowMs = now))
    }

    @Test
    fun `is clamped to one`() {
        assertEquals(1f, mediaProgress(200.0, 190.0, now - 60_000, playing = true, nowMs = now))
    }

    @Test
    fun `is null without duration`() {
        assertNull(mediaProgress(null, 50.0, now, playing = true, nowMs = now))
        assertNull(mediaProgress(0.0, 50.0, now, playing = true, nowMs = now))
    }

    @Test
    fun `paused stays at the position`() {
        assertEquals(0.25f, mediaProgress(200.0, 50.0, now - 10_000, playing = false, nowMs = now))
    }

    @Test
    fun `parseTint reads only #rrggbb`() {
        assertEquals(Color(0xFF3D2466), parseTint("#3d2466"))
        assertNull(parseTint("red"))
        assertNull(parseTint("#3d2466x"))
        assertNull(parseTint(null))
    }
}
