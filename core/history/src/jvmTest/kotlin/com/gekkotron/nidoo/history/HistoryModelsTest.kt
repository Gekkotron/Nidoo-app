package com.gekkotron.nidoo.history

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryModelsTest {
    @Test
    fun `energy scale follows the unit`() {
        assertEquals(EnergyScale(EnergyKind.COUNTER, 1.0), EnergyScale.of("kWh"))
        assertEquals(EnergyScale(EnergyKind.COUNTER, 0.001), EnergyScale.of("Wh"))
        assertEquals(EnergyScale(EnergyKind.POWER, 1.0), EnergyScale.of("W"))
        assertEquals(EnergyScale(EnergyKind.POWER, 1000.0), EnergyScale.of("kW"))
        assertNull(EnergyScale.of("°C"))
        assertNull(EnergyScale.of(null))
    }

    @Test
    fun `ranges use the hub's wire names, and only the day-spanning ones are buckets`() {
        assertEquals(listOf("1h", "6h", "24h", "7d", "30d", "1y"), HistoryRange.entries.map { it.query })
        assertEquals(listOf(HistoryRange.D7, HistoryRange.D30, HistoryRange.Y1), HistoryRange.entries.filter { it.bucketed })
    }
}
