package com.gekkotron.nidoo.feature.home.ui

import com.gekkotron.nidoo.dashboards.WidgetKind
import com.gekkotron.nidoo.dashboards.WidgetRange
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.DashboardScreenState
import com.gekkotron.nidoo.feature.home.presentation.DashboardUi
import com.gekkotron.nidoo.feature.home.presentation.DashboardView
import com.gekkotron.nidoo.feature.home.presentation.EnergyMetric
import com.gekkotron.nidoo.feature.home.presentation.EnergyValue
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.presentation.SwitcherEntry
import com.gekkotron.nidoo.feature.home.presentation.TileContent
import com.gekkotron.nidoo.feature.home.presentation.TileUi
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.history.HistoryPoint
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.util.Locale

/** Cards and dashboard states shared by the dashboard screen and screenshot tests. */
internal object DashboardFixtures {
    private val entities = listOf(
        testEntity("lamp", Kind.LIGHT, "Floor lamp", area = "living", capabilities = Capabilities(brightness = true)),
        testEntity("shutter", Kind.COVER, "Shutter", area = "living", capabilities = Capabilities(position = true)),
        testEntity("coffee", Kind.SWITCH, "Coffee machine", area = "kitchen"),
    ).associateBy { it.uniqueId }
    private val states = mapOf(
        "lamp" to EntityState("lamp", StateValue.Light(true, 180)),
        "shutter" to EntityState("shutter", StateValue.Cover(CoverState.OPEN, 60)),
        "coffee" to EntityState("coffee", StateValue.OnOff(false)),
    )
    private val areas = listOf(Area("living", "Living room"), Area("kitchen", "Kitchen"))

    val house = HomeUiBuilder.build(entities, states, areas, RoomFilter.All, LinkState.Connected(TransportMode.DEMO), Locale.US)

    fun card(id: String): CardUi = house.cards.first { it.id == id }

    val entries = listOf(SwitcherEntry("house", null), SwitcherEntry("evening", "Soirée"), SwitcherEntry("energy", "Énergie"))

    fun tile(key: String, content: TileContent, columns: Int = 1, rows: Int = 1, fullWidth: Boolean = false) =
        TileUi(key, columns, fullWidth, rows, content)

    fun shown(tiles: List<TileUi>, showHeader: Boolean = false, selectedId: String = "evening", name: String = "Soirée", running: Set<String> = emptySet()) =
        DashboardScreenState(
            switcherVisible = true, entries = entries, selectedId = selectedId,
            view = DashboardView.Shown(DashboardUi(selectedId, name, showHeader, tiles)), runningFlows = running,
        )

    /** One tile of every content type. */
    fun everything(): DashboardScreenState {
        val points = (0 until 24).map { HistoryPoint(1_700_000_000L + it * 3600L, 19.0 + it % 6, 18.0 + it % 6, 20.0 + it % 6) }
        return shown(
            listOf(
                tile("t1", TileContent.Entity(card("lamp"))),
                tile("t2", TileContent.Entity(card("shutter"))),
                tile("t3", TileContent.Room("Living room", listOf(card("lamp"), card("shutter"))), columns = 2),
                tile("t4", TileContent.FlowButton("goodnight", "Good night", true)),
                tile("t5", TileContent.FlowButton("away", "Away mode", false)),
                tile("t6", TileContent.EnergyStat(EnergyMetric.PV_POWER_NOW, null, EnergyValue.Watts(2450.0))),
                tile("t7", TileContent.EnergyStat(EnergyMetric.SELF_CONSUMPTION_PCT, null, EnergyValue.Percent(72))),
                tile("t8", TileContent.EnergyStat(EnergyMetric.TODAY_KWH, null, EnergyValue.Kwh(8.4)), columns = 2),
                tile("t9", TileContent.History("temp", "Bedroom temperature", WidgetRange.H24, "°C", points), columns = 2, rows = 2),
                tile("t10", TileContent.Placeholder(WidgetKind.LOCK, "Front door")),
                tile("t11", TileContent.Unavailable("Garage")),
            ),
            showHeader = true,
        )
    }
}
