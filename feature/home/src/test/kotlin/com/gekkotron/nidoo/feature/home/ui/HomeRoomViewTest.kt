package com.gekkotron.nidoo.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.HomeUiState
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals

/** Rooms design §6.4–§6.5: a room laid out by type, its strip, back and search, driven like the real screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class HomeRoomViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val areas = listOf(Area("living", "Living room"), Area("kitchen", "Kitchen"))
    private val entities: List<Entity> = listOf(
        testEntity("lamp", Kind.LIGHT, "Floor lamp", area = "living", ui = UiOverride(favorite = true)),
        testEntity("shutter", Kind.COVER, "Shutter", area = "living"),
        testEntity("plug", Kind.SWITCH, "TV plug", area = "living"),
        testEntity("temp", Kind.SENSOR, "Thermometer", area = "living", deviceClass = "temperature", unit = "°C"),
        testEntity("coffee", Kind.SWITCH, "Coffee machine", area = "kitchen"),
        testEntity("outdoor", Kind.SENSOR, "Outdoor", unit = "°C", ui = UiOverride(cardStyle = HomeUiBuilder.HERO_WEATHER)),
    )
    private val states = mapOf(
        "lamp" to EntityState("lamp", StateValue.Light(true)),
        "temp" to EntityState("temp", StateValue.Numeric(21.5)),
        "outdoor" to EntityState("outdoor", StateValue.Numeric(12.0)),
    )

    private var selected by mutableStateOf<RoomFilter>(RoomFilter.Rooms)
    private var query by mutableStateOf("")

    private fun build(list: List<Entity> = entities): HomeUiState = HomeUiBuilder.build(
        list.associateBy { it.uniqueId }, states, areas, selected, LinkState.Connected(TransportMode.DEMO), Locale.US, query = query,
    )

    /** The screen wired to a tiny stand-in for the view model: selecting clears the search, as it does there. */
    private fun show(start: RoomFilter, list: List<Entity> = entities) {
        selected = start
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                HomeScreen(
                    state = build(list),
                    onSelect = { selected = it; query = "" },
                    onTap = {}, onSend = { _, _ -> },
                    onSearch = { query = it },
                    now = LocalTime.NOON,
                )
            }
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `a room shows its name as the title with its status, not the greeting, and no weather hero`() {
        show(RoomFilter.Area("living"))
        compose.onNodeWithTag("room-title").assert(hasText("Living room"))
        compose.onNodeWithTag("room-status").assert(hasText("21.5 °C · 1 light on · Shutters open"))
        compose.onNodeWithText("Good afternoon").assertDoesNotExist()
        compose.onNodeWithTag("hero").assertDoesNotExist()
    }

    @Test
    fun `outside a room the greeting and the weather hero stay`() {
        show(RoomFilter.Rooms)
        compose.onNodeWithText("Good afternoon").assertExists()
        compose.onNodeWithTag("hero").assertExists()
        compose.onNodeWithTag("room-title").assertDoesNotExist()
    }

    @Test
    fun `a room lists its sections in order and its sensors as pills, not tiles`() {
        show(RoomFilter.Area("living"))
        val labels = listOf("section-LIGHTS", "section-SHUTTERS_CLIMATE", "section-PLUGS_MEDIA")
        val tops = labels.map { compose.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("LIGHTS").assertExists()
        compose.onNodeWithText("SHUTTERS & CLIMATE").assertExists()
        compose.onNodeWithText("PLUGS & MEDIA").assertExists()
        compose.onNodeWithTag("strip-temp").assertExists()
        compose.onNodeWithTag("card-temp").assertDoesNotExist()
        compose.onNodeWithTag("section-SENSORS").assertDoesNotExist()
        // The strip sits above the first section.
        assert(compose.onNodeWithTag("strip-temp").fetchSemanticsNode().positionInRoot.y < tops.first())
    }

    @Test
    fun `a room with a single section shows no label`() {
        show(RoomFilter.Area("kitchen"))
        compose.onNodeWithTag("card-coffee").assertExists()
        compose.onNodeWithTag("section-PLUGS_MEDIA").assertDoesNotExist()
    }

    @Test
    fun `system back from a room goes to Rooms`() {
        show(RoomFilter.Area("living"))
        back()
        assertEquals<RoomFilter>(RoomFilter.Rooms, selected)
        compose.onNodeWithTag("room-living").assertExists()
    }

    @Test
    fun `All devices shows sections with Sensors last and no strip`() {
        show(RoomFilter.All)
        compose.onNodeWithTag("strip-temp").assertDoesNotExist()
        compose.onNodeWithTag("home-grid").performScrollToNode(hasTestTag("card-temp"))
        compose.onNodeWithTag("card-temp").assertExists()
        val sensors = compose.onNodeWithTag("section-SENSORS").fetchSemanticsNode().positionInRoot.y
        val plugs = compose.onNodeWithTag("section-PLUGS_MEDIA").fetchSemanticsNode().positionInRoot.y
        assert(plugs < sensors)
    }

    @Test
    fun `Favourites stays a flat grid without labels`() {
        show(RoomFilter.Favorites)
        compose.onNodeWithTag("card-lamp").assertExists()
        compose.onNodeWithTag("section-LIGHTS").assertDoesNotExist()
    }

    @Test
    fun `every device of a room is on screen exactly once, as a tile or a pill`() {
        val everything = Kind.entries.map { testEntity(it.name.lowercase(), it, area = "living", ui = UiOverride(homePinned = true)) }
        show(RoomFilter.Area("living"), everything)
        for (e in everything) {
            compose.onNodeWithTag("home-grid").performScrollToNode(hasTestTag("card-${e.uniqueId}").or(hasTestTag("strip-${e.uniqueId}")))
            val tiles = compose.onAllNodesWithTag("card-${e.uniqueId}").fetchSemanticsNodes().size
            val pills = compose.onAllNodesWithTag("strip-${e.uniqueId}").fetchSemanticsNodes().size
            assertEquals(1, tiles + pills, e.uniqueId)
        }
    }
}
