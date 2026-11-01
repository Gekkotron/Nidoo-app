package com.gekkotron.oikos.feature.energy.presentation

import com.gekkotron.oikos.feature.energy.energyEntity
import com.gekkotron.oikos.feature.energy.numeric
import com.gekkotron.oikos.feature.energy.recorded
import com.gekkotron.oikos.history.DailyTotals
import com.gekkotron.oikos.history.DayTotal
import com.gekkotron.oikos.history.EnergyScale
import com.gekkotron.oikos.history.HistoryClient
import com.gekkotron.oikos.history.HistoryRange
import com.gekkotron.oikos.history.HistoryRef
import com.gekkotron.oikos.history.HistoryResult
import com.gekkotron.oikos.history.Series
import com.gekkotron.oikos.model.EnergyRole
import com.gekkotron.oikos.model.Entity
import com.gekkotron.oikos.transport.LinkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnergyStatsTest {
    private fun power(id: String, role: EnergyRole, device: String): Entity =
        energyEntity(id, role).copy(deviceId = device).recorded("power")

    private fun counter(id: String, device: String): Entity =
        energyEntity(id, null, unit = "kWh").copy(deviceId = device).recorded("energy")

    private fun map(vararg e: Entity) = e.associateBy { it.uniqueId }

    private val link = LinkState.Connecting

    /** Answers per meter id; `today` and `daily` (any day count) come from the maps, anything else is not recorded. */
    private class FakeHistory(
        val today: Map<String, Double> = emptyMap(),
        val daily: Map<String, List<Double>> = emptyMap(),
        on: Boolean = true,
    ) : HistoryClient {
        override val available: StateFlow<Boolean> = MutableStateFlow(on)
        val dailyCalls = mutableListOf<Pair<String, Int>>()
        override suspend fun series(ref: HistoryRef, range: HistoryRange): HistoryResult<Series> = HistoryResult.NotRecorded
        override suspend fun daily(ref: HistoryRef, days: Int, scale: EnergyScale): HistoryResult<DailyTotals> {
            dailyCalls += ref.id to days
            val v = daily[ref.id] ?: return HistoryResult.NotRecorded
            return HistoryResult.Ok(DailyTotals(v.mapIndexed { i, k -> DayTotal(i.toLong(), k) }))
        }
        override suspend fun today(ref: HistoryRef, scale: EnergyScale): HistoryResult<Double?> =
            today[ref.id]?.let { HistoryResult.Ok(it) } ?: HistoryResult.NotRecorded
    }

    // The house is measured: a power sensor with its own counter.
    private val measured = map(
        power("grid_power", EnergyRole.GRID, "g"),
        power("solar_power", EnergyRole.SOLAR, "s"),
        power("load_power", EnergyRole.HOUSE, "h"), counter("load_energy", "h"),
    )
    private val measuredStates = listOf(numeric("grid_power", -400.0), numeric("solar_power", 3200.0), numeric("load_power", 2800.0))
        .associateBy { it.uniqueId }

    // No house role: solar + grid import - grid export.
    private val derived = map(
        power("solar_power", EnergyRole.SOLAR, "s"), counter("solar_energy", "s"),
        power("grid_power", EnergyRole.GRID, "g"), counter("grid_energy", "g"), counter("grid_energy_produced", "g"),
    )

    @Test
    fun `live reads grid solar and self-consumption like the Energy tab`() {
        val ui = EnergyFlowBuilder.build(measured, measuredStates, link, 0)
        val live = EnergyStats.live(measured, measuredStates, link, 0)
        assertEquals(ui.node("grid")?.value, live.gridWatts)
        assertEquals(ui.node("solar")?.value, live.solarWatts)
        assertEquals(ui.selfConsumption, live.selfConsumption)
        assertEquals(400.0, live.gridWatts)
        assertEquals(3200.0, live.solarWatts)
    }

    @Test
    fun `today is the house node's today kWh`() = runBlocking {
        val h = FakeHistory(today = mapOf("load_energy" to 12.4))
        assertEquals(12.4, EnergyStats.consumption(measured, measuredStates, link, 0, h, days = 1))
    }

    @Test
    fun `week sums seven daily totals`() = runBlocking {
        val h = FakeHistory(daily = mapOf("load_energy" to List(7) { 1.5 }))
        assertEquals(10.5, EnergyStats.consumption(measured, measuredStates, link, 0, h, days = 7))
        assertEquals(true, ("load_energy" to 7) in h.dailyCalls)
    }

    @Test
    fun `derived house subtracts export over the period`() = runBlocking {
        val h = FakeHistory(
            daily = mapOf("solar_energy" to listOf(12.0, 8.0), "grid_energy" to listOf(5.0), "grid_energy_produced" to listOf(3.0, 5.0)),
        )
        assertEquals(17.0, EnergyStats.consumption(derived, emptyMap(), link, 0, h, days = 7))
    }

    @Test
    fun `history off gives null`() = runBlocking {
        assertNull(EnergyStats.consumption(measured, measuredStates, link, 0, HistoryClient.None, days = 1))
        assertNull(EnergyStats.consumption(measured, measuredStates, link, 0, HistoryClient.None, days = 30))
    }
}
