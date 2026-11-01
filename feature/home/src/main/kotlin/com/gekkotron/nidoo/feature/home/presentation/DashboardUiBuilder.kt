package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.dashboards.Dashboard
import com.gekkotron.nidoo.dashboards.DashboardLayout
import com.gekkotron.nidoo.dashboards.Widget
import com.gekkotron.nidoo.dashboards.WidgetKind
import com.gekkotron.nidoo.dashboards.WidgetRange
import com.gekkotron.nidoo.dashboards.WidgetSpan
import com.gekkotron.nidoo.flows.FlowDto
import com.gekkotron.nidoo.history.HistoryPoint
import com.gekkotron.nidoo.history.HistoryRange
import com.gekkotron.nidoo.history.HistoryRefs
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.HubGroup
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.transport.LinkState
import java.util.Locale

/** Everything of the House a dashboard tile can draw from. */
data class HouseData(
    val entities: Map<String, Entity>,
    val states: Map<String, EntityState>,
    val areas: List<Area>,
    val link: LinkState,
    val hubGroups: Map<String, HubGroup>,
    val historyEnabled: Boolean,
)

/** What tiles need beyond House state: flows, fetched energy figures and history series. */
data class TileExtras(
    val flows: List<FlowDto> = emptyList(),
    val energy: EnergyFigures = EnergyFigures(),
    /** A key that is present with a null value was fetched and failed (or is not recorded): "no data", not loading. */
    val series: Map<Pair<String, WidgetRange>, List<HistoryPoint>?> = emptyMap(),
    /** False until the flows snapshot has settled; an unknown flow is then "not loaded yet" rather than missing. */
    val flowsSettled: Boolean = true,
)

object DashboardUiBuilder {
    private const val PHONE_COLUMNS = 2
    private const val FULL_WIDTH_FROM = 4

    fun build(
        dashboard: Dashboard,
        house: HouseData,
        extras: TileExtras,
        locale: Locale,
        labels: LabelText,
        nowMillis: Long,
    ): DashboardUi {
        val tiles = DashboardLayout.readingOrder(dashboard.widgets).mapIndexed { index, w ->
            val content = if (w.span == WidgetSpan.UNKNOWN) {
                TileContent.Unavailable(w.config.title)
            } else {
                content(w, house, extras, locale, labels, nowMillis)
            }
            TileUi(
                key = "$index:${w.id}",
                columns = minOf(w.span.cols, PHONE_COLUMNS),
                fullWidth = w.span.cols >= FULL_WIDTH_FROM,
                rows = w.span.rows,
                content = content,
            )
        }
        return DashboardUi(dashboard.id, dashboard.name, dashboard.showHeader, tiles)
    }

    /** The service only knows 24 h, 7 d, 30 d and 1 y: the short ranges are cut from the 24 h series. */
    fun historyRange(range: WidgetRange): HistoryRange = when (range) {
        WidgetRange.D7 -> HistoryRange.D7
        WidgetRange.D30 -> HistoryRange.D30
        else -> HistoryRange.H24
    }

    private fun content(w: Widget, h: HouseData, x: TileExtras, locale: Locale, labels: LabelText, now: Long): TileContent {
        val c = w.config
        return when (w.kind) {
            WidgetKind.LIGHT, WidgetKind.COVER, WidgetKind.CLIMATE, WidgetKind.SENSOR, WidgetKind.WATER_HEATER -> {
                val card = c.entityId?.let { HomeUiBuilder.cardFor(h.entities, h.states, h.areas, it, locale, labels, h.historyEnabled) }
                when {
                    card == null -> TileContent.Unavailable(c.title)
                    else -> TileContent.Entity(if (c.title != null) card.copy(name = c.title!!) else card)
                }
            }
            WidgetKind.ROOM_GROUP -> room(c.areaId, c.title, h, locale, labels)
            WidgetKind.FLOW_BUTTON -> {
                val flow = x.flows.firstOrNull { it.id == c.flowId }
                when {
                    flow != null -> TileContent.FlowButton(flow.id, c.title ?: flow.name, flow.enabled)
                    !x.flowsSettled && c.flowId != null -> TileContent.FlowButton(c.flowId!!, c.title ?: c.flowId!!, false)
                    else -> TileContent.Unavailable(c.title)
                }
            }
            WidgetKind.ENERGY_STAT -> {
                val metric = EnergyMetric.of(c.metric)
                if (metric == null) TileContent.Unavailable(c.title) else TileContent.EnergyStat(metric, c.title, energyValue(metric, x.energy))
            }
            WidgetKind.HISTORY_CHART -> history(w, h, x, locale, labels, now)
            WidgetKind.LOCK -> TileContent.Placeholder(w.kind, c.title ?: c.entityId?.let { h.entities[it]?.name })
            WidgetKind.CAMERA -> TileContent.Placeholder(w.kind, c.title ?: c.cameras.joinToString(", ").takeIf { it.isNotEmpty() })
            WidgetKind.UNKNOWN -> TileContent.Unavailable(c.title)
        }
    }

    private fun room(areaId: String?, title: String?, h: HouseData, locale: Locale, labels: LabelText): TileContent {
        val area = h.areas.firstOrNull { it.id == areaId } ?: return TileContent.Unavailable(title)
        val filter = RoomFilter.Area(area.id)
        val ui = HomeUiBuilder.build(h.entities, h.states, h.areas, filter, h.link, locale, labels, historyEnabled = h.historyEnabled, hubGroups = h.hubGroups)
        // build() falls back to another chip when the room has no visible card; that is not this room's cards.
        val cards = if (ui.selected == filter) ui.cards.filter { it.kind == Kind.LIGHT || it.kind == Kind.COVER } else emptyList()
        return if (cards.isEmpty()) TileContent.Unavailable(title ?: area.name) else TileContent.Room(title ?: area.name, cards)
    }

    private fun energyValue(metric: EnergyMetric, e: EnergyFigures): EnergyValue? = when (metric) {
        EnergyMetric.TODAY_KWH, EnergyMetric.WEEK_KWH, EnergyMetric.MONTH_KWH -> e.kwhByDays[metric.days]?.let(EnergyValue::Kwh)
        EnergyMetric.GRID_POWER_NOW -> e.live?.gridWatts?.let(EnergyValue::Watts)
        EnergyMetric.PV_POWER_NOW -> e.live?.solarWatts?.let(EnergyValue::Watts)
        EnergyMetric.SELF_CONSUMPTION_PCT -> e.live?.selfConsumption?.let(EnergyValue::Percent)
    }

    private fun history(w: Widget, h: HouseData, x: TileExtras, locale: Locale, labels: LabelText, nowMillis: Long): TileContent {
        val c = w.config
        val entity = c.entityId?.let { h.entities[it] }
        val range = c.range
        if (entity == null || HistoryRefs.of(entity) == null || range == null || range == WidgetRange.UNKNOWN) {
            return TileContent.Unavailable(c.title)
        }
        if (!h.historyEnabled) return TileContent.Unavailable(c.title)
        // Series timestamps are epoch seconds.
        val since = when (range) {
            WidgetRange.H1 -> nowMillis / 1000 - 3_600
            WidgetRange.H6 -> nowMillis / 1000 - 6 * 3_600
            else -> null
        }
        val key = entity.uniqueId to range
        val name = c.title ?: HomeUiBuilder.cardFor(h.entities, h.states, h.areas, entity.uniqueId, locale, labels, h.historyEnabled)?.name ?: entity.name
        if (key in x.series && x.series[key] == null) return TileContent.Unavailable(name)
        val points = x.series[key]?.let { pts -> if (since == null) pts else pts.filter { it.t >= since } }
        return TileContent.History(entity.uniqueId, name, range, entity.unit, points)
    }
}
