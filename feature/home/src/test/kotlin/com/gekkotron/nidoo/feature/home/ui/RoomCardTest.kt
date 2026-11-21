package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.feature.home.presentation.RoomCardUi
import com.gekkotron.nidoo.feature.home.presentation.RoomIcon
import com.gekkotron.nidoo.feature.home.presentation.RoomTarget
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class RoomCardTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var lights = 0
    private var shutters = 0

    private fun light(id: String, on: Boolean, available: Boolean = true) = RoomTarget(id, id, available, stale = false, on = on)
    private fun shutter(id: String, open: Boolean, moving: CoverState? = null) = RoomTarget(id, id, available = true, stale = false, on = open, moving = moving)

    private fun room(
        lights: List<RoomTarget> = emptyList(),
        shutters: List<RoomTarget> = emptyList(),
        temperature: CardValue.Measurement? = null,
        humidity: CardValue.Measurement? = null,
        devices: Int = lights.size + shutters.size,
    ) = RoomCardUi("living", "Living room", RoomIcon.LIVING_ROOM, temperature, humidity, lights, shutters, devices, buttons = true)

    private fun show(room: RoomCardUi) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            RoomCard(room, onOpen = { opened++ }, onLights = { lights++ }, onShutters = { shutters++ })
        }
    }

    @Test
    fun `shows the name and the status line`() {
        show(
            room(
                lights = listOf(light("a", true), light("b", true), light("c", false)),
                shutters = listOf(shutter("s", true)),
                temperature = CardValue.Measurement("21.5", "°C"), humidity = CardValue.Measurement("48", "%"),
            ),
        )
        compose.onNodeWithText("Living room").assertExists()
        compose.onNodeWithText("21.5 °C · 48 % · 2 lights on · Shutters open").assertExists()
    }

    @Test
    fun `one light on is singular and no light on reads Lights off`() {
        show(room(lights = listOf(light("a", true), light("b", false))))
        compose.onNodeWithText("1 light on").assertExists()
    }

    @Test
    fun `lights off and shutters closed`() {
        show(room(lights = listOf(light("a", false)), shutters = listOf(shutter("s", false))))
        compose.onNodeWithText("Lights off · Shutters closed").assertExists()
    }

    @Test
    fun `status without lights or sensors is the device count`() {
        show(room(devices = 4))
        compose.onNodeWithText("4 devices").assertExists()
        compose.onNodeWithTag("room-living-lights").assertDoesNotExist()
        compose.onNodeWithTag("room-living-shutters").assertDoesNotExist()
    }

    @Test
    fun `mixed shutters read n of m open`() {
        show(room(shutters = listOf(shutter("a", true), shutter("b", false), shutter("c", false))))
        compose.onNodeWithText("1 of 3 open").assertExists()
    }

    @Test
    fun `moving shutters read their direction`() {
        show(room(shutters = listOf(shutter("a", true, moving = CoverState.CLOSING), shutter("b", false))))
        compose.onNodeWithText("Closing…").assertExists()
        compose.onNodeWithContentDescription("Stop Living room shutters").assertExists()
    }

    @Test
    fun `tapping the card opens the room, tapping a button does not`() {
        show(room(lights = listOf(light("a", true)), shutters = listOf(shutter("s", true))))
        compose.onNodeWithTag("room-living-lights").performClick()
        compose.onNodeWithTag("room-living-shutters").performClick()
        assertEquals(listOf(0, 1, 1), listOf(opened, lights, shutters))
        compose.onNodeWithText("Living room").performClick()
        assertEquals(listOf(1, 1, 1), listOf(opened, lights, shutters))
    }

    @Test
    fun `the buttons say what they will do`() {
        show(room(lights = listOf(light("a", true)), shutters = listOf(shutter("s", true))))
        compose.onNodeWithContentDescription("Turn off Living room lights").assertExists()
        compose.onNodeWithContentDescription("Close Living room shutters").assertExists()
        val state = compose.onNodeWithTag("room-living-lights").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("On", state)
    }

    @Test
    fun `closed shutters and dark lights offer to open and turn on`() {
        show(room(lights = listOf(light("a", false)), shutters = listOf(shutter("s", false))))
        compose.onNodeWithContentDescription("Turn on Living room lights").assertExists()
        compose.onNodeWithContentDescription("Open Living room shutters").assertExists()
    }

    @Test
    fun `Unassigned has no buttons and the localized name`() {
        show(RoomCardUi(UNASSIGNED_AREA, null, RoomIcon.GENERIC, null, null, listOf(light("a", true)), emptyList(), 1, buttons = false))
        compose.onNodeWithText("Unassigned").assertExists()
        compose.onNodeWithTag("room-$UNASSIGNED_AREA-lights").assertDoesNotExist()
    }

    @Test
    fun `a room with only unavailable lights shows a disabled lights button`() {
        show(room(lights = listOf(light("a", true, available = false))))
        compose.onNodeWithTag("room-living-lights").assertIsNotEnabled()
        compose.onNodeWithTag("room-living-lights").performClick()
        assertEquals(0, lights)
        compose.onNodeWithText("Lights off").assertExists()
    }

    @Test
    fun `the card reads as one sentence for TalkBack`() {
        show(room(lights = listOf(light("a", true))))
        compose.onNodeWithContentDescription("Living room, 1 light on").assertExists()
    }
}
