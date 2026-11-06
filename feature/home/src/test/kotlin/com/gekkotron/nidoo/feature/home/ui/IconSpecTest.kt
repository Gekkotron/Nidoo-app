package com.gekkotron.nidoo.feature.home.ui

import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.VacuumMotion
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.transport.LinkState
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconSpecTest {
    private fun spec(kind: Kind, value: StateValue?, deviceClass: String? = null, available: Boolean = true): IconSpec {
        // Pinned: a door or motion sensor would otherwise only be on Security.
        val e = testEntity("e", kind, deviceClass = deviceClass, ui = UiOverride(homePinned = true))
        val states = value?.let { mapOf("e" to EntityState("e", it, available = available)) } ?: emptyMap()
        val card = HomeUiBuilder.build(mapOf("e" to e), states, emptyList(), RoomFilter.All, LinkState.Connecting, Locale.US).card("e")!!
        return iconSpec(card)
    }

    @Test
    fun `a shutter targets its position, clamped, whatever its state`() {
        assertEquals(IconSpec.Shutter(0.6f, null), spec(Kind.COVER, StateValue.Cover(CoverState.OPEN, 60)))
        assertEquals(IconSpec.Shutter(0.3f, null), spec(Kind.COVER, StateValue.Cover(CoverState.STOPPED, 30)))
        assertEquals(IconSpec.Shutter(0.3f, null), spec(Kind.COVER, StateValue.Cover(CoverState.UNKNOWN, 30)))
        assertEquals(IconSpec.Shutter(1f, null), spec(Kind.COVER, StateValue.Cover(CoverState.OPEN, 101)))
        assertEquals(IconSpec.Shutter(0f, null), spec(Kind.COVER, StateValue.Cover(CoverState.CLOSED, -1)))
    }

    @Test
    fun `a shutter without position is fully open or closed, else keeps its drawing`() {
        assertEquals(IconSpec.Shutter(1f, null), spec(Kind.COVER, StateValue.Cover(CoverState.OPEN)))
        assertEquals(IconSpec.Shutter(0f, null), spec(Kind.COVER, StateValue.Cover(CoverState.CLOSED)))
        assertEquals(IconSpec.Shutter(null, null), spec(Kind.COVER, StateValue.Cover(CoverState.STOPPED)))
        assertEquals(IconSpec.Shutter(null, null), spec(Kind.COVER, null))
    }

    @Test
    fun `a moving shutter carries its direction`() {
        assertEquals(IconSpec.Shutter(0.4f, CoverState.OPENING), spec(Kind.COVER, StateValue.Cover(CoverState.OPENING, 40)))
        assertEquals(IconSpec.Shutter(null, CoverState.CLOSING), spec(Kind.COVER, StateValue.Cover(CoverState.CLOSING)))
    }

    @Test
    fun `doors, windows and garage doors swing with their contact`() {
        assertEquals(IconSpec.Swing(SwingShape.DOOR, true), spec(Kind.BINARY_SENSOR, StateValue.OnOff(true), "door"))
        assertEquals(IconSpec.Swing(SwingShape.DOOR, false), spec(Kind.BINARY_SENSOR, StateValue.OnOff(false), "opening"))
        assertEquals(IconSpec.Swing(SwingShape.WINDOW, true), spec(Kind.BINARY_SENSOR, StateValue.OnOff(true), "window"))
        assertEquals(IconSpec.Swing(SwingShape.GARAGE, false), spec(Kind.BINARY_SENSOR, StateValue.OnOff(false), "garage_door"))
    }

    @Test
    fun `an unknown or unavailable contact is neutral and not an open contact`() {
        assertEquals(IconSpec.Swing(SwingShape.DOOR, null), spec(Kind.BINARY_SENSOR, null, "door"))
        val unavailable = spec(Kind.BINARY_SENSOR, StateValue.OnOff(true), "door", available = false)
        assertEquals(IconSpec.Swing(SwingShape.DOOR, null), unavailable)
        assertFalse(unavailable.openContact)
        assertTrue(spec(Kind.BINARY_SENSOR, StateValue.OnOff(true), "window").openContact)
    }

    @Test
    fun `other kinds get their static glyph, lit only for lights and switches that are on`() {
        assertEquals(IconSpec.Static(NidooGlyph.BULB, true), spec(Kind.LIGHT, StateValue.Light(true, 180)))
        assertEquals(IconSpec.Static(NidooGlyph.PLUG, false), spec(Kind.SWITCH, StateValue.OnOff(false)))
        assertEquals(IconSpec.Static(NidooGlyph.THERMOMETER, false), spec(Kind.SENSOR, StateValue.Numeric(20.0), "temperature"))
        assertEquals(IconSpec.Static(NidooGlyph.DROP, false), spec(Kind.SENSOR, StateValue.Numeric(50.0), "humidity"))
        assertEquals(IconSpec.Static(NidooGlyph.BOLT, false), spec(Kind.SENSOR, StateValue.Numeric(5.0), "energy"))
        assertEquals(IconSpec.Static(NidooGlyph.TREND, false), spec(Kind.SENSOR, StateValue.Numeric(5.0), "pm25"))
        assertEquals(IconSpec.Static(NidooGlyph.WALKER, false), spec(Kind.BINARY_SENSOR, StateValue.OnOff(true), "motion"))
        assertEquals(IconSpec.Static(NidooGlyph.FLAME, false), spec(Kind.BINARY_SENSOR, StateValue.OnOff(false), "smoke"))
        assertEquals(IconSpec.Static(NidooGlyph.DOT, false), spec(Kind.BINARY_SENSOR, StateValue.OnOff(false), "vibration"))
        assertEquals(IconSpec.Climate(running = false, fanMode = null), spec(Kind.CLIMATE, null))
        assertEquals(IconSpec.Climate(running = false, fanMode = "silence"), spec(Kind.CLIMATE, StateValue.Climate(mode = "off", target = 21.0, current = 22.0, fanMode = "silence")))
        assertEquals(IconSpec.Climate(running = true, fanMode = "high"), spec(Kind.CLIMATE, StateValue.Climate(mode = "cool", target = 21.0, current = 22.0, fanMode = "high")))
        assertEquals(IconSpec.Climate(running = false, fanMode = "high"), spec(Kind.CLIMATE, StateValue.Climate(mode = "cool", target = 21.0, current = 22.0, fanMode = "high"), available = false))
        assertEquals(IconSpec.Static(NidooGlyph.SHIELD, false), spec(Kind.ALARM_PANEL, null))
        assertEquals(IconSpec.Static(NidooGlyph.SLIDER, false), spec(Kind.NUMBER, null))
        assertEquals(IconSpec.Static(NidooGlyph.LIST, false), spec(Kind.SELECT, null))
        assertEquals(IconSpec.Static(NidooGlyph.PLAY, false), spec(Kind.BUTTON, null))
        assertEquals(IconSpec.Vacuum(motion = VacuumMotion.IDLE, fanSpeed = null), spec(Kind.VACUUM, null))
        assertEquals(IconSpec.Vacuum(motion = VacuumMotion.IDLE, fanSpeed = "balanced"), spec(Kind.VACUUM, StateValue.Vacuum("docked", 80, "balanced")))
        assertEquals(IconSpec.Vacuum(motion = VacuumMotion.CLEANING, fanSpeed = "turbo"), spec(Kind.VACUUM, StateValue.Vacuum("cleaning", 60, "turbo")))
        assertEquals(IconSpec.Vacuum(motion = VacuumMotion.RETURNING, fanSpeed = "max"), spec(Kind.VACUUM, StateValue.Vacuum("returning", 40, "max")))
    }

    @Test
    fun `an unavailable shutter keeps its last position but stops moving`() {
        assertEquals(IconSpec.Shutter(0.4f, null), spec(Kind.COVER, StateValue.Cover(CoverState.CLOSING, 40), available = false))
        assertEquals(IconSpec.Shutter(null, null), spec(Kind.COVER, StateValue.Cover(CoverState.OPENING), available = false))
    }

    @Test
    fun `a cover group's shutter icon follows the mean position and motion`() {
        val entities = listOf(
            testEntity("bay", Kind.COVER, area = "living"),
            testEntity("side", Kind.COVER, area = "living"),
            testEntity("patio", Kind.COVER, area = "living"),
        ).associateBy { it.uniqueId }
        val states = mapOf(
            "bay" to EntityState("bay", StateValue.Cover(CoverState.OPEN, 100)),
            "side" to EntityState("side", StateValue.Cover(CoverState.OPEN, 80)),
            "patio" to EntityState("patio", StateValue.Cover(CoverState.CLOSED, null)),
        )
        val group = HomeUiBuilder.build(entities, states, listOf(Area("living", "Living room")), RoomFilter.All, LinkState.Connecting, Locale.US)
            .cards.single { it.isGroup }
        assertEquals(IconSpec.Shutter(0.6f, null), iconSpec(group))
        val moving = HomeUiBuilder.build(
            entities, states + ("side" to EntityState("side", StateValue.Cover(CoverState.CLOSING, 50))),
            listOf(Area("living", "Living room")), RoomFilter.All, LinkState.Connecting, Locale.US,
        ).cards.single { it.isGroup }
        assertEquals(IconSpec.Shutter(0.5f, CoverState.CLOSING), iconSpec(moving))
    }

    @Test
    fun `a media player wears the note, lit while playing`() {
        val playing = StateValue.MediaPlayer("playing", "Song", null, null, null, null, null, null, null)
        assertEquals(IconSpec.Static(NidooGlyph.MUSIC, on = true), spec(Kind.MEDIA_PLAYER, playing))
        assertEquals(IconSpec.Static(NidooGlyph.MUSIC, on = false), spec(Kind.MEDIA_PLAYER, playing.copy(state = "paused")))
    }
}
