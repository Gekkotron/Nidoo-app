package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.energy.presentation.EnergyFormat
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode
import com.gekkotron.nidoo.feature.energy.presentation.EnergySankey
import com.gekkotron.nidoo.feature.energy.presentation.EnergyUiState
import com.gekkotron.nidoo.feature.energy.presentation.Flow
import com.gekkotron.nidoo.feature.energy.presentation.NodeKind
import com.gekkotron.nidoo.feature.energy.presentation.OrbiteLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class OrbiteDiagramTest {
    @get:Rule
    val compose = createComposeRule()

    private fun role(id: String, kind: NodeKind, w: Double) = EnergyNode(id = id, kind = kind, value = w, flow = Flow.IN)
    private val oven = EnergyNode(id = "c:oven", kind = NodeKind.CONSUMER, name = "Oven", value = 600.0, flow = Flow.IN)
    private val house = EnergyUiState(
        loading = false,
        diagram = listOf(role("grid", NodeKind.GRID, 2260.0), role("house", NodeKind.HOUSE, 2260.0)),
        cards = listOf(oven),
        consumers = listOf(oven),
    )

    private fun show(s: EnergyUiState, onMore: () -> Unit = {}, onNode: (EnergyNode) -> Unit = {}) = compose.setContent {
        NidooTheme(ThemeChoice.IVOIRE) { OrbiteDiagram(s, Locale.US, onMore = onMore, onNode = onNode) }
    }

    @Test
    fun orbiteHitFindsTheArcUnderAFinger() {
        val rings = OrbiteLayout.rings(EnergySankey.build(house))
        val arc = rings.outer.first { it.id == oven.id }
        val (x, y) = OrbiteLayout.point(50.0, 50.0, 100.0 * OrbiteGeometry.OUTER_R, (arc.a0 + arc.a1) / 2)
        assertEquals(oven.id, orbiteHit(rings, x.toFloat(), y.toFloat(), 100f))
        assertEquals("grid", orbiteHit(rings, 50f, 50f - 100f * OrbiteGeometry.INNER_R, 100f))
        assertNull(orbiteHit(rings, 50f, 50f, 100f))
    }

    @Test
    fun orbiteEmptyStateShowsNoTotal() {
        show(EnergyUiState())
        compose.onNodeWithTag("energy-orbite").assertExists()
        assertEquals(0, compose.onAllNodesWithText(" W", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun orbiteShowsHouseTotal() {
        show(house)
        compose.onNodeWithText(EnergyFormat.power(2260.0, Locale.US)).assertExists()
    }

    @Test
    fun orbiteTapOnConsumerArcOpensNode() {
        var opened: EnergyNode? = null
        show(house, onNode = { opened = it })
        val arc = OrbiteLayout.rings(EnergySankey.build(house)).outer.first { it.id == oven.id }
        compose.onNodeWithTag("energy-orbite-rings").performTouchInput {
            val side = width.toFloat()
            val (x, y) = OrbiteLayout.point(side / 2.0, side / 2.0, side * OrbiteGeometry.OUTER_R.toDouble(), (arc.a0 + arc.a1) / 2)
            click(Offset(x.toFloat(), y.toFloat()))
        }
        assertEquals(oven, opened)
    }
}
