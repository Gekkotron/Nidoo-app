package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.model.Hs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconPulseTest {
    @Test
    fun `the siren pulse stays within its bounds and rests at phase zero`() {
        assertEquals(1f, pulseScale(0f))
        assertEquals(1f, pulseAlpha(0f))
        for (i in 0..20) {
            val t = i / 20f
            assertTrue(pulseScale(t) in 1f..1.14f + 1e-6f, "scale at $t")
            assertTrue(pulseAlpha(t) in 0.65f - 1e-6f..1f, "alpha at $t")
        }
        assertEquals(pulseScale(1f), pulseScale(7f), "phase is clamped")
    }

    @Test
    fun `the glow is off when the light is off`() {
        for (i in 0..10) assertEquals(0f, glowAlpha(lit = false, t = i / 10f))
    }

    @Test
    fun `the glow breathes within a soft range when the light is on`() {
        for (i in 0..10) assertTrue(glowAlpha(lit = true, t = i / 10f) in 0.22f - 1e-6f..0.42f + 1e-6f)
        assertTrue(glowAlpha(true, 1f) > glowAlpha(true, 0f))
        assertTrue(glowAlpha(true, GLOW_STILL) in 0.22f..0.42f, "reduced motion draws one steady level")
    }

    @Test
    fun `the glow takes the light colour, else the on tint`() {
        val tint = Color(0xFFFFB300)
        assertEquals(tint, glowColor(null, tint))
        assertEquals(Color.hsv(0f, 1f, 1f), glowColor(Hs(0.0, 1.0), tint))
        assertEquals(Color.hsv(120f, 0.5f, 1f), glowColor(Hs(120.0, 0.5), tint))
    }
}
