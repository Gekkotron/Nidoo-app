package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.test.assertIsDisplayed
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
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class GroupCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val entities = listOf(
        testEntity("bay", Kind.COVER, "Bay window", area = "living", capabilities = Capabilities(position = true)),
        testEntity("side", Kind.COVER, "Side window", area = "living", capabilities = Capabilities(position = true)),
        testEntity("patio", Kind.COVER, "Patio door", area = "living"),
        testEntity("l1", Kind.LIGHT, "Ceiling", area = "living"),
        testEntity("l2", Kind.LIGHT, "Floor lamp", area = "living"),
    ).associateBy { it.uniqueId }
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
                state = HomeUiBuilder.build(entities, states, listOf(Area("living", "Living room")), RoomFilter.All, LinkState.Connected(TransportMode.DEMO), Locale.US),
                onSelect = {},
                onTap = { taps += it.id },
                onSend = { id, c -> sends += id to c },
                onSendGroup = { id, c -> groupSends += id to c },
                now = LocalTime.NOON,
            )
        }
    }

    @Test
    fun `the shutter group card shows its summary and sends from its inline buttons`() {
        show()
        compose.onNodeWithText("Shutters").assertIsDisplayed()
        compose.onNodeWithText("2 open · 1 closed").assertIsDisplayed()
        compose.onNodeWithText("×3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close all shutters").performClick()
        assertEquals(listOf("group:living:cover" to Command.Close as Command), groupSends)
    }

    @Test
    fun `the light group card opens its sheet on tap`() {
        show()
        compose.onNodeWithText("1 of 2 on").assertIsDisplayed()
        compose.onNodeWithTag("card-group:living:light").performClick()
        // Sheet marker: the per-member row for one of the lights is visible.
        compose.onNodeWithText("Ceiling").assertIsDisplayed()
        assertEquals(emptyList(), taps)
    }

    @Test
    fun `the light group card toggles the whole group from its inline button`() {
        show()
        // One of the two lights is on, so the group reads on; the toggle turns them all off.
        compose.onNodeWithTag("group-toggle-group:living:light").performClick()
        assertEquals(listOf("group:living:light" to Command.TurnOff as Command), groupSends)
    }

    @Test
    fun `tapping the shutter group opens its sheet`() {
        show()
        compose.onNodeWithTag("card-group:living:cover").performClick()
        compose.onNodeWithText("Bay window").assertIsDisplayed()
    }
}
