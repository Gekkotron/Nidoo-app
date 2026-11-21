package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.presentation.TapAction
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class SensorStripTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()
    private val longClicks = mutableListOf<String>()

    private fun temperature(id: String, name: String) = testEntity(id, Kind.SENSOR, name, area = "living", deviceClass = "temperature", unit = "°C")

    private fun strip(vararg entities: Entity, states: Map<String, EntityState> = emptyMap()): List<CardUi> = HomeUiBuilder.build(
        entities.associateBy { it.uniqueId }, states, listOf(Area("living", "Living room")),
        RoomFilter.Area("living"), LinkState.Connected(TransportMode.DEMO), Locale.US,
    ).strip

    private fun show(cards: List<CardUi>) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            SensorStrip(cards, onClick = { clicks += it.id }, onLongClick = { longClicks += it.id })
        }
    }

    @Test
    fun `a lone temperature sensor shows its value without its name`() {
        show(strip(temperature("t", "Thermometer"), states = mapOf("t" to EntityState("t", StateValue.Numeric(21.5)))))
        compose.onNodeWithText("21.5 °C", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Thermometer", useUnmergedTree = true).assertDoesNotExist()
        // TalkBack still gets the name, the value and the room.
        compose.onNodeWithTag("strip-t").assertExists()
        assertEquals(
            listOf("Thermometer, 21.5 °C, Living room"),
            compose.onNodeWithTag("strip-t").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun `two temperature sensors keep their names`() {
        show(strip(temperature("a", "Window side"), temperature("b", "Sofa side")))
        compose.onNodeWithText("Window side", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sofa side", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `any other sensor keeps its name`() {
        show(strip(testEntity("lux", Kind.SENSOR, "Brightness", area = "living", deviceClass = "illuminance", unit = "lx")))
        compose.onNodeWithText("Brightness", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a sensor with history opens it on tap`() {
        show(strip(temperature("t", "Thermometer")).map { it.copy(tap = TapAction.OPEN_HISTORY, hasHistory = true) })
        compose.onNodeWithTag("strip-t").performClick()
        assertEquals(listOf("t"), clicks)
    }

    @Test
    fun `a sensor without history ignores the tap and offers no click to TalkBack`() {
        show(strip(temperature("t", "Thermometer")))
        compose.onNodeWithTag("strip-t").performClick()
        assertEquals(emptyList(), clicks)
        assertFalse(SemanticsActions.OnClick in compose.onNodeWithTag("strip-t").fetchSemanticsNode().config)
    }

    @Test
    fun `long-press customizes`() {
        show(strip(temperature("t", "Thermometer")))
        compose.onNodeWithTag("strip-t").performTouchInput { longClick() }
        assertEquals(listOf("t"), longClicks)
        assertEquals("Customize", compose.onNodeWithTag("strip-t").fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun `an unavailable sensor reads Unavailable`() {
        show(strip(temperature("t", "Thermometer"), states = mapOf("t" to EntityState("t", StateValue.Numeric(21.5), available = false))))
        compose.onNodeWithText("Unavailable", useUnmergedTree = true).assertExists()
    }
}
