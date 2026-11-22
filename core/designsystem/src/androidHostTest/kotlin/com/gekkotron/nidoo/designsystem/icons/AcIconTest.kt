package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.designsystem.NightGlassColors
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcIconTest {
    @Test
    fun `each mode takes its theme colour`() {
        val c = NightGlassColors
        val n = Color.White
        assertEquals(c.water, acModeColor(AcMode.COOL, c, n))
        assertEquals(c.heat, acModeColor(AcMode.HEAT, c, n))
        assertEquals(c.water, acModeColor(AcMode.DRY, c, n))
        assertEquals(n, acModeColor(AcMode.FAN, c, n))
        assertEquals(c.connected, acModeColor(AcMode.AUTO, c, n))
    }

    @Test
    fun `airflow speed follows the fan mode, baseline for anything unknown`() {
        assertEquals(2400, airflowPeriodMs("silence"))
        assertEquals(2400, airflowPeriodMs("Quiet"))
        assertEquals(2000, airflowPeriodMs("2"))
        assertEquals(1600, airflowPeriodMs("medium"))
        assertEquals(1300, airflowPeriodMs("4"))
        assertEquals(1000, airflowPeriodMs("high"))
        assertEquals(1800, airflowPeriodMs(null))
        assertEquals(1800, airflowPeriodMs("auto"))
        assertEquals(1800, airflowPeriodMs("Standard"))
    }

    @Test
    fun `the vane hangs below the unit and swings clear of its outline`() {
        assertEquals(14.3f, vaneY(0f), 1e-4f)
        assertEquals(14.7f, vaneY(0.25f), 1e-4f)
        assertEquals(13.9f, vaneY(0.75f), 1e-4f)
        // The unit's bottom outline ends at 12.4 + 0.8: the vane stroke (1.3 wide) never sits on it.
        assertTrue(vaneY(0.75f) - 0.65f > 12.4f + 0.8f)
    }
}
