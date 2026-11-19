package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.designsystem.NightGlassColors
import org.junit.Test
import kotlin.test.assertEquals

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
}
