package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.IvoireColors
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode
import com.gekkotron.nidoo.feature.energy.presentation.EnergySankey
import com.gekkotron.nidoo.feature.energy.presentation.EnergyUiState
import com.gekkotron.nidoo.feature.energy.presentation.Flow
import com.gekkotron.nidoo.feature.energy.presentation.Frame
import com.gekkotron.nidoo.feature.energy.presentation.NodeKind
import com.gekkotron.nidoo.feature.energy.presentation.NodeStatus
import com.gekkotron.nidoo.feature.energy.presentation.SankeyLayout
import com.gekkotron.nidoo.feature.energy.presentation.SankeyRole
import com.gekkotron.nidoo.feature.energy.presentation.hit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class FluxDiagramTest {
    @get:Rule
    val compose = createComposeRule()

    private fun role(id: String, kind: NodeKind, w: Double, flow: Flow = Flow.IN, status: NodeStatus = NodeStatus.LIVE) =
        EnergyNode(id = id, kind = kind, value = w, flow = flow, status = status)

    private fun consumer(name: String, w: Double) =
        EnergyNode(id = "c:" + name.lowercase(), kind = NodeKind.CONSUMER, name = name, value = w, flow = Flow.IN)

    private fun state(roles: List<EnergyNode>, consumers: List<EnergyNode> = emptyList()) =
        EnergyUiState(loading = false, diagram = roles, cards = consumers, consumers = consumers)

    private val oven = consumer("Oven", 600.0)
    private val house = state(listOf(role("grid", NodeKind.GRID, 1000.0), role("house", NodeKind.HOUSE, 1000.0)), listOf(oven))

    private fun show(s: EnergyUiState, onMore: () -> Unit = {}, onNode: (EnergyNode) -> Unit = {}) = compose.setContent {
        NidooTheme(ThemeChoice.IVOIRE) { FluxDiagram(s, Locale.US, onMore = onMore, onNode = onNode) }
    }

    @Test
    fun sankeyColorRotatesConsumerSlots() {
        assertEquals(IvoireColors.energyConsumers[1], sankeyColor(SankeyRole.CONSUMER, 6, IvoireColors))
        assertEquals(IvoireColors.grid, sankeyColor(SankeyRole.EXPORT, 0, IvoireColors))
        assertEquals(IvoireColors.energyMuted, sankeyColor(SankeyRole.UNITEMISED, 0, IvoireColors))
    }

    @Test
    fun hitFindsBarsThenBands() {
        val placed = SankeyLayout.place(EnergySankey.build(house), Frame.NARROW)
        val bar = placed.bars.first { it.id == oven.id }
        assertEquals(oven.id, placed.hit(bar.x + bar.w / 2, bar.y + bar.h / 2, Frame.NARROW, 0.0))
        val band = placed.bands.first { it.to == oven.id }
        val midX = (band.a0.x + band.a1.x + band.b0.x + band.b1.x) / 4
        val midY = (band.a0.y + band.b0.y) / 2
        assertEquals(oven.id, placed.hit(midX, midY, Frame.NARROW, 0.0))
        assertNull(placed.hit(1.0, 1.0, Frame.NARROW, 0.0))
    }

    @Test
    fun fluxDrawsNothingForEmptySankey() {
        show(EnergyUiState())
        compose.onNodeWithTag("energy-flux").assertExists()
        compose.onNodeWithTag("flux-house").assertDoesNotExist()
        assertEquals(0, compose.onAllNodesWithText(" W", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun fluxShowsNotReportingCaption() {
        show(state(listOf(role("grid", NodeKind.GRID, 0.0, status = NodeStatus.STALE), role("solar", NodeKind.SOLAR, 800.0), role("house", NodeKind.HOUSE, 800.0))))
        compose.onNodeWithText("Not reporting: Grid").assertExists()
    }

    @Test
    fun tapOthersOpensConsumers() {
        var more = 0
        val cs = (1..6).map { consumer("Load$it", 100.0) }
        show(state(listOf(role("grid", NodeKind.GRID, 1000.0), role("house", NodeKind.HOUSE, 1000.0)), cs), onMore = { more++ })
        compose.onNodeWithTag("flux-others").performClick()
        assertEquals(1, more)
    }

    @Test
    fun tapConsumerOpensItsNode() {
        var opened: EnergyNode? = null
        show(house, onNode = { opened = it })
        compose.onNodeWithTag("flux-${oven.id}").performClick()
        assertEquals(oven, opened)
    }
}
