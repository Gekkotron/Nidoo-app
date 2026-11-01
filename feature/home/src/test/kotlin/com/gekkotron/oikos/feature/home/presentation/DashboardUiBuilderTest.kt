package com.gekkotron.oikos.feature.home.presentation

import com.gekkotron.oikos.dashboards.Cell
import com.gekkotron.oikos.dashboards.Dashboard
import com.gekkotron.oikos.dashboards.Widget
import com.gekkotron.oikos.dashboards.WidgetConfig
import com.gekkotron.oikos.dashboards.WidgetKind
import com.gekkotron.oikos.dashboards.WidgetRange
import com.gekkotron.oikos.dashboards.WidgetSpan
import com.gekkotron.oikos.feature.energy.presentation.EnergyStats
import com.gekkotron.oikos.feature.home.testEntity
import com.gekkotron.oikos.flows.FlowDto
import com.gekkotron.oikos.history.HistoryPoint
import com.gekkotron.oikos.model.Area
import com.gekkotron.oikos.model.EnergyRole
import com.gekkotron.oikos.model.Entity
import com.gekkotron.oikos.model.Kind
import com.gekkotron.oikos.model.UiOverride
import com.gekkotron.oikos.transport.LinkState
import com.gekkotron.oikos.transport.TransportMode
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DashboardUiBuilderTest {
    private val areas = listOf(Area("kitchen", "Kitchen"), Area("living", "Living room"))
    private val now = 1_800_000_000_000L

    private fun house(vararg e: Entity, historyEnabled: Boolean = true) = HouseData(
        e.associateBy { it.uniqueId }, emptyMap(), areas, LinkState.Connected(TransportMode.DEMO), emptyMap(), historyEnabled,
    )

    private fun w(
        id: String,
        kind: WidgetKind,
        span: WidgetSpan = WidgetSpan.S1X1,
        config: WidgetConfig = WidgetConfig(),
        pos: Cell? = null,
    ) = Widget(id, kind, span, config, pos)

    private fun dash(vararg widgets: Widget) = Dashboard("d", "Dash", 0, true, widgets.toList())

    private fun build(d: Dashboard, h: HouseData, x: TileExtras = TileExtras()) =
        DashboardUiBuilder.build(d, h, x, Locale.US, LabelText { null }, now)

    private fun light(id: String, w: String = id, cfg: WidgetConfig = WidgetConfig(entityId = id), pos: Cell? = null) =
        w(w, WidgetKind.LIGHT, config = cfg, pos = pos)

    @Test
    fun `tiles follow reading order not list order`() {
        val ui = build(
            dash(light("a", pos = Cell(1, 0)), light("b", pos = Cell(0, 0))),
            house(testEntity("a", Kind.LIGHT), testEntity("b", Kind.LIGHT)),
        )
        assertEquals(listOf("b", "a"), ui.tiles.map { (it.content as TileContent.Entity).card.id })
        assertEquals(listOf("0:b", "1:a"), ui.tiles.map { it.key })
    }

    @Test
    fun `tile keys are unique with duplicate widget ids and entities`() {
        val cfg = WidgetConfig(entityId = "a")
        val ui = build(dash(light("a", "w", cfg), light("a", "w", cfg)), house(testEntity("a", Kind.LIGHT)))
        assertEquals(2, ui.tiles.size)
        assertEquals(2, ui.tiles.map { it.key }.toSet().size)
    }

    @Test
    fun `spans map to phone cells`() {
        val expected = listOf(
            WidgetSpan.S1X1 to Triple(1, false, 1),
            WidgetSpan.S2X1 to Triple(2, false, 1),
            WidgetSpan.S1X2 to Triple(1, false, 2),
            WidgetSpan.S2X2 to Triple(2, false, 2),
            WidgetSpan.S4X2 to Triple(2, true, 2),
            WidgetSpan.S6X3 to Triple(2, true, 3),
        )
        for ((span, cell) in expected) {
            val t = build(dash(w("x", WidgetKind.LIGHT, span, WidgetConfig(entityId = "a"))), house(testEntity("a", Kind.LIGHT))).tiles.single()
            assertEquals(cell, Triple(t.columns, t.fullWidth, t.rows), span.name)
        }
    }

    @Test
    fun `widget title renames the card`() {
        val ui = build(dash(light("a", cfg = WidgetConfig(entityId = "a", title = "Reading"))), house(testEntity("a", Kind.LIGHT, name = "Lamp")))
        assertEquals("Reading", (ui.tiles.single().content as TileContent.Entity).card.name)
    }

    @Test
    fun `unknown entity, kind, span, metric, area and flow are Unavailable`() {
        val ui = build(
            dash(
                light("ghost"),
                w("k", WidgetKind.UNKNOWN),
                w("s", WidgetKind.LIGHT, WidgetSpan.UNKNOWN, WidgetConfig(entityId = "a")),
                w("m", WidgetKind.ENERGY_STAT, config = WidgetConfig(metric = "bogus")),
                w("r", WidgetKind.ROOM_GROUP, config = WidgetConfig(areaId = "nowhere")),
                w("f", WidgetKind.FLOW_BUTTON, config = WidgetConfig(flowId = "none")),
            ),
            house(testEntity("a", Kind.LIGHT)),
        )
        assertEquals(6, ui.tiles.size)
        ui.tiles.forEach { assertIs<TileContent.Unavailable>(it.content) }
        assertEquals(1, ui.tiles.first { it.key.endsWith(":s") }.rows)
    }

    @Test
    fun `energy-role and hidden entities still render`() {
        val ui = build(
            dash(
                w("a", WidgetKind.SENSOR, config = WidgetConfig(entityId = "pv")),
                w("b", WidgetKind.LIGHT, config = WidgetConfig(entityId = "h")),
            ),
            house(
                testEntity("pv", Kind.SENSOR, unit = "W", ui = UiOverride(energyRole = EnergyRole.SOLAR)),
                testEntity("h", Kind.LIGHT, ui = UiOverride(hidden = true)),
            ),
        )
        ui.tiles.forEach { assertIs<TileContent.Entity>(it.content) }
    }

    @Test
    fun `companion entity is Unavailable`() {
        val ui = build(dash(light("c")), house(testEntity("parent", Kind.LIGHT), testEntity("c", Kind.LIGHT, partOf = "parent")))
        assertIs<TileContent.Unavailable>(ui.tiles.single().content)
    }

    @Test
    fun `room group shows the room's light and cover cards`() {
        val ui = build(
            dash(w("r", WidgetKind.ROOM_GROUP, config = WidgetConfig(areaId = "kitchen"))),
            house(
                testEntity("l1", Kind.LIGHT, area = "kitchen"), testEntity("l2", Kind.LIGHT, area = "kitchen"),
                testEntity("c1", Kind.COVER, area = "kitchen"), testEntity("c2", Kind.COVER, area = "kitchen"),
                testEntity("t", Kind.SENSOR, area = "kitchen", unit = "C"),
            ),
        )
        val room = assertIs<TileContent.Room>(ui.tiles.single().content)
        assertEquals("Kitchen", room.title)
        assertEquals(listOf(Kind.COVER, Kind.LIGHT), room.cards.map { it.kind })
        assertEquals(true, room.cards.all { it.isGroup })
        assertNotNull(ui.card("l1"))
    }

    @Test
    fun `room without lights or covers is Unavailable`() {
        val ui = build(
            dash(w("r", WidgetKind.ROOM_GROUP, config = WidgetConfig(areaId = "living"))),
            house(testEntity("l1", Kind.LIGHT, area = "kitchen")),
        )
        assertIs<TileContent.Unavailable>(ui.tiles.single().content)
    }

    @Test
    fun `flow button uses the flow name and enabled flag`() {
        val x = TileExtras(flows = listOf(FlowDto("f1", "Night mode", enabled = false)))
        val ui = build(dash(w("f", WidgetKind.FLOW_BUTTON, config = WidgetConfig(flowId = "f1"))), house(), x)
        assertEquals(TileContent.FlowButton("f1", "Night mode", false), ui.tiles.single().content)
        val titled = build(dash(w("f", WidgetKind.FLOW_BUTTON, config = WidgetConfig(flowId = "f1", title = "Go"))), house(), x)
        assertEquals(TileContent.FlowButton("f1", "Go", false), titled.tiles.single().content)
    }

    @Test
    fun `a flow button whose flow is unknown waits disabled until flows settle`() {
        val d = dash(
            w("f", WidgetKind.FLOW_BUTTON, config = WidgetConfig(flowId = "f1")),
            w("g", WidgetKind.FLOW_BUTTON, config = WidgetConfig(flowId = "f2", title = "Go")),
        )
        val unsettled = build(d, house(), TileExtras(flowsSettled = false))
        assertEquals(
            listOf<TileContent>(TileContent.FlowButton("f2", "Go", false), TileContent.FlowButton("f1", "f1", false)).toSet(),
            unsettled.tiles.map { it.content }.toSet(),
        )
        build(d, house(), TileExtras(flowsSettled = true)).tiles.forEach { assertIs<TileContent.Unavailable>(it.content) }
    }

    @Test
    fun `history is Unavailable when the history service is off`() {
        val cfg = WidgetConfig(entityId = "t", range = WidgetRange.H24)
        val ui = build(dash(w("h", WidgetKind.HISTORY_CHART, config = cfg)), house(testEntity("t", Kind.SENSOR, unit = "C"), historyEnabled = false))
        assertIs<TileContent.Unavailable>(ui.tiles.single().content)
    }

    @Test
    fun `a failed history fetch is no data, not loading`() {
        val cfg = WidgetConfig(entityId = "t", range = WidgetRange.H24, title = "Bedroom")
        val e = testEntity("t", Kind.SENSOR, unit = "C")
        val ui = build(dash(w("h", WidgetKind.HISTORY_CHART, config = cfg)), house(e), TileExtras(series = mapOf(("t" to WidgetRange.H24) to null)))
        assertEquals(TileContent.Unavailable("Bedroom"), ui.tiles.single().content)
    }

    @Test
    fun `energy stat values`() {
        val x = TileExtras(energy = EnergyFigures(mapOf(1 to 12.4), EnergyStats.Live(300.0, 1200.0, null)))
        fun v(metric: String) = (build(dash(w("e", WidgetKind.ENERGY_STAT, config = WidgetConfig(metric = metric))), house(), x).tiles.single().content as TileContent.EnergyStat).value
        assertEquals(EnergyValue.Kwh(12.4), v("today_kwh"))
        assertEquals(EnergyValue.Watts(1200.0), v("pv_power_now"))
        assertEquals(EnergyValue.Watts(300.0), v("grid_power_now"))
        assertNull(v("self_consumption_pct"))
        assertNull(v("week_kwh"))
    }

    @Test
    fun `one-hour history keeps the last hour of the 24h series`() {
        val nowS = now / 1000
        val pts = listOf(nowS - 7_200, nowS - 3_599, nowS).map { HistoryPoint(it, 1.0, 0.0, 2.0) }
        val cfg = WidgetConfig(entityId = "t", range = WidgetRange.H1)
        val e = testEntity("t", Kind.SENSOR, unit = "C")
        val ui = build(dash(w("h", WidgetKind.HISTORY_CHART, config = cfg)), house(e), TileExtras(series = mapOf(("t" to WidgetRange.H1) to pts)))
        assertEquals(pts.drop(1), (ui.tiles.single().content as TileContent.History).points)
        val loading = build(dash(w("h", WidgetKind.HISTORY_CHART, config = cfg)), house(e))
        assertNull((loading.tiles.single().content as TileContent.History).points)
        assertEquals(com.gekkotron.oikos.history.HistoryRange.H24, DashboardUiBuilder.historyRange(WidgetRange.H6))
        assertEquals(com.gekkotron.oikos.history.HistoryRange.D7, DashboardUiBuilder.historyRange(WidgetRange.D7))
    }

    @Test
    fun `history of an entity without history or with an unknown range is Unavailable`() {
        val ui = build(
            dash(
                w("a", WidgetKind.HISTORY_CHART, config = WidgetConfig(entityId = "l", range = WidgetRange.D7)),
                w("b", WidgetKind.HISTORY_CHART, config = WidgetConfig(entityId = "t", range = WidgetRange.UNKNOWN)),
            ),
            house(testEntity("l", Kind.LIGHT), testEntity("t", Kind.SENSOR, unit = "C")),
        )
        ui.tiles.forEach { assertIs<TileContent.Unavailable>(it.content) }
    }

    @Test
    fun `lock and camera are placeholders`() {
        val ui = build(
            dash(
                w("l", WidgetKind.LOCK, config = WidgetConfig(entityId = "door")),
                w("c", WidgetKind.CAMERA, config = WidgetConfig(cameras = listOf("front", "back"))),
            ),
            house(testEntity("door", Kind.SENSOR, name = "Front door")),
        )
        assertEquals(TileContent.Placeholder(WidgetKind.LOCK, "Front door"), ui.tiles[0].content)
        assertEquals(TileContent.Placeholder(WidgetKind.CAMERA, "front, back"), ui.tiles[1].content)
    }

    @Test
    fun `card() finds group members on a dashboard`() {
        val ui = build(
            dash(w("r", WidgetKind.ROOM_GROUP, config = WidgetConfig(areaId = "kitchen"))),
            house(testEntity("l1", Kind.LIGHT, area = "kitchen"), testEntity("l2", Kind.LIGHT, area = "kitchen")),
        )
        assertEquals("l2", ui.card("l2")?.id)
        assertNull(ui.card("zzz"))
    }
}
