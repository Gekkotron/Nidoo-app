package com.gekkotron.nidoo.feature.energy.presentation

import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.history.HistoryResult
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.transport.LinkState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The figures of a dashboard energy-stat widget, computed by the Energy tab's own builder so the two agree. */
object EnergyStats {
    data class Live(val gridWatts: Double?, val solarWatts: Double?, val selfConsumption: Int?)

    fun live(entities: Map<String, Entity>, states: Map<String, EntityState>, link: LinkState, nowMillis: Long): Live {
        val ui = EnergyFlowBuilder.build(entities, states, link, nowMillis)
        return Live(ui.node("grid")?.value, ui.node("solar")?.value, ui.selfConsumption)
    }

    /**
     * House consumption in kWh over [days] (1 = since midnight); null when unknown. Each meter's total for the period
     * feeds the builder's `today` input, so a derived house subtracts export over the period like it does for today.
     * A meter that fails to answer is left out, which makes the total unknown.
     */
    suspend fun consumption(
        entities: Map<String, Entity>,
        states: Map<String, EntityState>,
        link: LinkState,
        nowMillis: Long,
        history: HistoryClient,
        days: Int,
    ): Double? {
        val totals = coroutineScope {
            EnergyToday.requests(entities).map { (id, r) ->
                async {
                    val (ref, scale) = r
                    val value: Double? = if (days <= 1) {
                        (history.today(ref, scale) as? HistoryResult.Ok)?.value
                    } else {
                        (history.daily(ref, days, scale) as? HistoryResult.Ok)?.value?.days?.sumOf { it.kwh }
                    }
                    id to value
                }
            }.awaitAll()
        }.mapNotNull { (id, v) -> v?.let { id to it } }.toMap()
        return EnergyFlowBuilder.build(entities, states, link, nowMillis, today = totals).node("house")?.todayKwh
    }
}
