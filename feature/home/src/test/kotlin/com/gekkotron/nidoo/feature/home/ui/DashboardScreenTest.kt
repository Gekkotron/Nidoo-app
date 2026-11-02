package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.DashboardScreenState
import com.gekkotron.nidoo.feature.home.presentation.DashboardView
import com.gekkotron.nidoo.feature.home.presentation.EnergyMetric
import com.gekkotron.nidoo.feature.home.presentation.EnergyValue
import com.gekkotron.nidoo.feature.home.presentation.TileContent
import com.gekkotron.nidoo.feature.home.ui.DashboardFixtures.card
import com.gekkotron.nidoo.feature.home.ui.DashboardFixtures.shown
import com.gekkotron.nidoo.feature.home.ui.DashboardFixtures.tile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val picks = mutableListOf<String>()
    private val runs = mutableListOf<String>()
    private val energyOpens = mutableListOf<Unit>()

    private fun show(dashboard: DashboardScreenState) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            HomeScreen(
                state = DashboardFixtures.house, onSelect = {}, onTap = {}, onSend = { _, _ -> }, now = LocalTime.NOON,
                dashboard = dashboard,
                onSelectDashboard = { picks += it },
                onRunFlow = { runs += it },
                onOpenEnergy = { energyOpens += Unit },
            )
        }
    }

    @Test
    fun `no switcher without dashboards`() {
        show(DashboardScreenState())
        compose.onNodeWithText("House ▾").assertDoesNotExist()
        compose.onNodeWithText("Good afternoon").assertExists()
        compose.onNodeWithTag("card-coffee").assertExists()
    }

    @Test
    fun `switcher opens the picker with House first`() {
        show(DashboardScreenState(switcherVisible = true, entries = DashboardFixtures.entries))
        compose.onNodeWithText("House ▾").performClick()
        val house = compose.onNode(hasText("House") and androidx.compose.ui.test.hasClickAction()).getBoundsInRoot()
        val evening = compose.onNode(hasText("Soirée") and androidx.compose.ui.test.hasClickAction()).getBoundsInRoot()
        assertTrue(house.top < evening.top)
        compose.onNodeWithText("Dashboards").assertExists()
    }

    @Test
    fun `picking a dashboard calls onSelectDashboard`() {
        show(DashboardScreenState(switcherVisible = true, entries = DashboardFixtures.entries))
        compose.onNodeWithText("House ▾").performClick()
        compose.onNode(hasText("Énergie") and androidx.compose.ui.test.hasClickAction()).performClick()
        assertEquals(listOf("energy"), picks)
    }

    @Test
    fun `dashboard tiles replace House cards`() {
        show(shown(listOf(tile("a", TileContent.Entity(card("lamp"))))))
        compose.onNodeWithTag("card-lamp").assertExists()
        compose.onNodeWithTag("card-coffee").assertDoesNotExist()
        compose.onNodeWithText("Good afternoon").assertDoesNotExist()
        compose.onNodeWithText("Soirée ▾").assertExists()
    }

    @Test
    fun `a dashboard with a header shows the greeting and room chips`() {
        show(shown(listOf(tile("a", TileContent.Entity(card("lamp")))), showHeader = true))
        compose.onNodeWithText("Good afternoon").assertExists()
        compose.onNodeWithText("Living room").assertExists()
    }

    @Test
    fun `an empty dashboard says so`() {
        show(shown(emptyList()))
        compose.onNodeWithText("Nothing here yet — add widgets on the web").assertExists()
    }

    @Test
    fun `loading shows the saved name and the waiting line`() {
        show(
            DashboardScreenState(
                switcherVisible = true, entries = DashboardFixtures.entries, selectedId = "evening",
                view = DashboardView.Loading("Soirée"),
            ),
        )
        compose.onNodeWithText("Soirée ▾").assertExists()
        compose.onNodeWithText("Waiting for the hub…").assertExists()
        compose.onNodeWithTag("card-coffee").assertDoesNotExist()
    }

    @Test
    fun `switcher announces the dashboard`() {
        show(shown(emptyList()))
        val node = compose.onNode(hasContentDescription("Dashboard: Soirée")).fetchSemanticsNode()
        assertEquals(listOf("Dashboard: Soirée"), node.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `unavailable tile renders for an unresolved widget`() {
        show(shown(listOf(tile("a", TileContent.Unavailable("Garage")))))
        compose.onNodeWithText("Garage").assertExists()
        compose.onNodeWithText("Unavailable").assertExists()
    }

    @Test
    fun `flow button runs and is disabled when the flow is off`() {
        show(
            shown(
                listOf(
                    tile("a", TileContent.FlowButton("goodnight", "Good night", true)),
                    tile("b", TileContent.FlowButton("away", "Away mode", false)),
                ),
            ),
        )
        compose.onNodeWithTag("flow-goodnight").performClick()
        assertEquals(listOf("goodnight"), runs)
        compose.onNodeWithTag("flow-away").assertIsNotEnabled()
        compose.onNodeWithText("This flow is off").assertExists()
    }

    @Test
    fun `energy stat shows its figure and opens Energy`() {
        show(shown(listOf(tile("a", TileContent.EnergyStat(EnergyMetric.PV_POWER_NOW, null, EnergyValue.Watts(2450.0))))))
        compose.onNodeWithText("Solar now").assertExists()
        compose.onNodeWithTag("energy-pv_power_now").performClick()
        assertEquals(1, energyOpens.size)
    }

    @Test
    fun `energy stat without a value shows a dash`() {
        show(shown(listOf(tile("a", TileContent.EnergyStat(EnergyMetric.TODAY_KWH, null, null)))))
        compose.onNodeWithText("—").assertExists()
    }

    @Test
    fun `search on a dashboard shows House results`() {
        show(shown(listOf(tile("a", TileContent.Entity(card("lamp"))))))
        compose.onNodeWithTag("card-coffee").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search devices").performClick()
        compose.onNodeWithTag("card-coffee").assertExists()
    }

    @Test
    fun `long-pressing a dashboard card House does not show opens its controls`() {
        val garage = card("lamp").copy(id = "garage-lamp", name = "Garage lamp")
        show(shown(listOf(tile("a", TileContent.Entity(garage)))))
        compose.onNodeWithTag("card-garage-lamp").performTouchInput { longClick() }
        compose.onNodeWithText("Brightness").assertExists()
    }
}
