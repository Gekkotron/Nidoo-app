package com.gekkotron.oikos.feature.home.presentation

import com.gekkotron.oikos.dashboards.HOUSE_DASHBOARD_ID
import com.gekkotron.oikos.dashboards.WidgetKind
import com.gekkotron.oikos.dashboards.WidgetRange
import com.gekkotron.oikos.feature.energy.presentation.EnergyStats
import com.gekkotron.oikos.history.HistoryPoint

/** What an `energy_stat` widget can show; [days] is the history window of a kWh metric (1 = since midnight). */
enum class EnergyMetric(val wire: String, val days: Int?) {
    TODAY_KWH("today_kwh", 1),
    WEEK_KWH("week_kwh", 7),
    MONTH_KWH("month_kwh", 30),
    GRID_POWER_NOW("grid_power_now", null),
    PV_POWER_NOW("pv_power_now", null),
    SELF_CONSUMPTION_PCT("self_consumption_pct", null),
    ;

    companion object {
        fun of(wire: String?): EnergyMetric? = entries.firstOrNull { it.wire == wire }
    }
}

sealed interface EnergyValue {
    data class Kwh(val v: Double) : EnergyValue
    data class Watts(val v: Double) : EnergyValue
    data class Percent(val v: Int) : EnergyValue
}

data class EnergyFigures(val kwhByDays: Map<Int, Double> = emptyMap(), val live: EnergyStats.Live? = null)

sealed interface TileContent {
    data class Entity(val card: CardUi) : TileContent
    data class Room(val title: String, val cards: List<CardUi>) : TileContent
    data class FlowButton(val flowId: String, val name: String, val enabled: Boolean) : TileContent
    data class EnergyStat(val metric: EnergyMetric, val title: String?, val value: EnergyValue?) : TileContent

    /** [points] null = still loading. */
    data class History(
        val entityId: String,
        val title: String,
        val range: WidgetRange,
        val unit: String?,
        val points: List<HistoryPoint>?,
    ) : TileContent

    /** Lock and camera: drawn as a stub for now. */
    data class Placeholder(val kind: WidgetKind, val title: String?) : TileContent
    data class Unavailable(val title: String?) : TileContent
}

data class TileUi(val key: String, val columns: Int, val fullWidth: Boolean, val rows: Int, val content: TileContent)

private fun search(cards: List<CardUi>, id: String): CardUi? = cards.firstNotNullOfOrNull { c ->
    c.takeIf { it.id == id } ?: search(c.companions, id) ?: search(c.members, id)
}

data class DashboardUi(val id: String, val name: String, val showHeader: Boolean, val tiles: List<TileUi>) {
    /** A card on this dashboard, group members and companions included. */
    fun card(id: String): CardUi? = search(
        tiles.flatMap { t ->
            when (val c = t.content) {
                is TileContent.Entity -> listOf(c.card)
                is TileContent.Room -> c.cards
                else -> emptyList()
            }
        },
        id,
    )
}

sealed interface DashboardView {
    data object House : DashboardView
    data class Loading(val name: String?) : DashboardView
    data class Shown(val ui: DashboardUi) : DashboardView
}

/** House: id [HOUSE_DASHBOARD_ID], name null (the UI localizes it). */
data class SwitcherEntry(val id: String, val name: String?)

data class DashboardScreenState(
    val switcherVisible: Boolean = false,
    val entries: List<SwitcherEntry> = emptyList(),
    val selectedId: String = HOUSE_DASHBOARD_ID,
    val view: DashboardView = DashboardView.House,
    val runningFlows: Set<String> = emptySet(),
) {
    fun card(id: String): CardUi? = (view as? DashboardView.Shown)?.ui?.card(id)
}

sealed interface DashboardEvent {
    /** [refused]: the hub answered 422 (disabled, invalid or busy); otherwise the request could not be made. */
    data class FlowRunFailed(val refused: Boolean) : DashboardEvent
}
