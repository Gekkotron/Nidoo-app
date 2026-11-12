package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.feature.home.withLivingHubGroups
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class GroupSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val house = withLivingHubGroups(listOf(
        testEntity("bay", Kind.COVER, "Bay window", area = "living", capabilities = Capabilities(position = true)),
        testEntity("side", Kind.COVER, "Side window", area = "living", capabilities = Capabilities(position = true)),
        testEntity("patio", Kind.COVER, "Patio door", area = "living"),
        testEntity("l1", Kind.LIGHT, "Ceiling", area = "living"),
        testEntity("l2", Kind.LIGHT, "Floor lamp", area = "living"),
    ))
    private val states = mapOf(
        "bay" to EntityState("bay", StateValue.Cover(CoverState.OPEN, 100)),
        "side" to EntityState("side", StateValue.Cover(CoverState.OPEN, 80)),
        "patio" to EntityState("patio", StateValue.Cover(CoverState.CLOSED, null)),
        "l1" to EntityState("l1", StateValue.Light(true, 200)),
    )
    private val sends = mutableListOf<Pair<String, Command>>()
    private val groupSends = mutableListOf<Pair<String, Command>>()
    private val taps = mutableListOf<String>()

    private fun show() = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            HomeScreen(
                state = HomeUiBuilder.build(house.entities, states, listOf(Area("living", "Living room")), RoomFilter.All, LinkState.Connected(TransportMode.DEMO), Locale.US, hubGroups = house.hubGroups),
                onSelect = {},
                onTap = { taps += it.id },
                onSend = { id, c -> sends += id to c },
                onSendGroup = { id, c -> groupSends += id to c },
                now = LocalTime.NOON,
            )
        }
    }

    @Test
    fun `the shutter sheet sends Open to the group and lists every member`() {
        show()
        compose.onNodeWithTag("card-group_cover_living").performClick()
        compose.onNodeWithText("Living room · 3 shutters").assertIsDisplayed()
        // The group's Open button comes before the "Open" state text of the Bay window row.
        compose.onAllNodesWithText("Open").onFirst().performClick()
        assertEquals("group_cover_living" to Command.Open as Command, groupSends.last())
        compose.onNodeWithTag("member-patio").assertExists()
    }

    @Test
    fun `a member row's own button sends to that member only`() {
        show()
        compose.onNodeWithTag("card-group_cover_living").performClick()
        compose.onNodeWithTag("member-close-side").performClick()
        assertEquals(listOf("side" to Command.Close as Command), sends)
    }

    @Test
    fun `tapping a member expands its inline controls while the group sheet stays open`() {
        show()
        compose.onNodeWithTag("card-group_cover_living").performClick()
        compose.onAllNodesWithTag("member-position-bay").assertCountEquals(0)
        compose.onNodeWithTag("member-bay").performClick()
        // The position slider only exists after the row has been expanded; the group header is still visible.
        compose.onNodeWithTag("member-position-bay").assertExists()
        compose.onNodeWithText("Living room · 3 shutters").assertIsDisplayed()
        // Tapping again collapses the row.
        compose.onNodeWithTag("member-bay").performClick()
        compose.onAllNodesWithTag("member-position-bay").assertCountEquals(0)
    }

    @Test
    fun `long-pressing a member opens its customize sheet`() {
        show()
        compose.onNodeWithTag("card-group_cover_living").performClick()
        compose.onNodeWithTag("member-bay").performTouchInput { longClick() }
        // Customize sheet marker: the Name field label is unique to it.
        compose.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun `tapping the light group opens its sheet with a master switch`() {
        show()
        compose.onNodeWithTag("card-group_light_living").performClick()
        compose.onNodeWithText("Living room · 2 lights").assertIsDisplayed()
        compose.onNodeWithTag("group-power-group_light_living").performClick()
        assertEquals("group_light_living" to Command.TurnOff as Command, groupSends.last())
    }

    @Test
    fun `long-pressing the light group opens its sheet so each light is driven on its own`() {
        show()
        compose.onNodeWithTag("card-group_light_living").performTouchInput { longClick() }
        compose.onNodeWithText("Living room · 2 lights").assertIsDisplayed()
        compose.onNodeWithTag("member-l1").assertExists()
        compose.onNodeWithTag("member-l2").assertExists()
        compose.onNode(hasContentDescription("Floor lamp") and isToggleable()).performClick()
        assertEquals(listOf("l2" to Command.TurnOn() as Command), sends)
        assertEquals(emptyList(), groupSends)
    }

    @Test
    fun `long-pressing the shutter group still opens its customize sheet`() {
        show()
        compose.onNodeWithTag("card-group_cover_living").performTouchInput { longClick() }
        // Group Customize marker: only it has a Save button; the group sheet has none.
        compose.onNodeWithText("Save").assertExists()
        compose.onAllNodesWithTag("member-bay").assertCountEquals(0)
    }

    @Test
    fun `every light switch in the group sheet is named for TalkBack`() {
        show()
        compose.onNodeWithTag("card-group_light_living").performClick()
        compose.onNode(hasContentDescription("Ceiling") and isToggleable()).assertExists()
        compose.onNode(hasContentDescription("Floor lamp") and isToggleable()).assertExists()
        compose.onNode(hasContentDescription("Power") and isToggleable()).assertExists()
    }
}
