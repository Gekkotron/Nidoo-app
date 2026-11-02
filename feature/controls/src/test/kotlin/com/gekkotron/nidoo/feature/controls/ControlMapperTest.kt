package com.gekkotron.nidoo.feature.controls

import com.gekkotron.nidoo.feature.controls.model.ControlAction
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType
import com.gekkotron.nidoo.feature.widget.Failure
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlMapperTest {
    private object NoBindings : Bindings

    private fun entity(
        kind: Kind,
        deviceClass: String? = null,
        caps: Capabilities = Capabilities(),
        area: String = UNASSIGNED_AREA,
        ui: UiOverride = UiOverride(),
        partOf: String? = null,
        protocolKind: Kind = kind,
    ) = Entity("id", null, "Lamp", kind, deviceClass, null, null, caps, NoBindings, area, ui, partOf, protocolKind = protocolKind)

    private fun state(value: StateValue, available: Boolean = true) = EntityState("id", value, available = available)

    private fun model(on: Boolean = false, dimmable: Boolean = true) =
        ControlModel("id", "Lamp", null, ControlType.LIGHT, dimmable, on, null, ControlStatus.OK)

    @Test
    fun lights_and_switches_are_eligible_others_are_not() {
        assertTrue(ControlMapper.eligible(entity(Kind.LIGHT)))
        assertTrue(ControlMapper.eligible(entity(Kind.SWITCH)))
        assertFalse(ControlMapper.eligible(entity(Kind.COVER)))
        assertFalse(ControlMapper.eligible(entity(Kind.SENSOR)))
        assertFalse(ControlMapper.eligible(entity(Kind.LIGHT, ui = UiOverride(hidden = true))))
        assertFalse(ControlMapper.eligible(entity(Kind.LIGHT, partOf = "x")))
    }

    @Test
    fun switch_as_light_is_a_plain_light_toggle() {
        val m = ControlMapper.model(entity(Kind.LIGHT, protocolKind = Kind.SWITCH, caps = Capabilities(brightness = false)), null, emptyList())
        assertEquals(ControlType.LIGHT, m.type)
        assertFalse(m.dimmable)
    }

    @Test
    fun outlet_device_class_maps_to_outlet() {
        assertEquals(ControlType.OUTLET, ControlMapper.model(entity(Kind.SWITCH, deviceClass = "outlet"), null, emptyList()).type)
        assertEquals(ControlType.SWITCH, ControlMapper.model(entity(Kind.SWITCH), null, emptyList()).type)
    }

    @Test
    fun zone_is_the_area_name_or_null_when_unassigned() {
        val areas = listOf(Area("salon", "Salon"))
        assertEquals("Salon", ControlMapper.model(entity(Kind.LIGHT, area = "salon"), null, areas).zone)
        assertNull(ControlMapper.model(entity(Kind.LIGHT, area = UNASSIGNED_AREA), null, areas).zone)
        assertNull(ControlMapper.model(entity(Kind.LIGHT, area = "garage"), null, areas).zone)
    }

    @Test
    fun light_state_maps_to_on_and_percent() {
        val e = entity(Kind.LIGHT, caps = Capabilities(brightness = true))
        val half = ControlMapper.model(e, state(StateValue.Light(true, 128)), emptyList())
        assertTrue(half.on)
        assertEquals(50, half.brightnessPercent)
        assertEquals(100, ControlMapper.model(e, state(StateValue.Light(true, 255)), emptyList()).brightnessPercent)
    }

    @Test
    fun entity_without_state_shows_off_and_ok() {
        val e = entity(Kind.LIGHT, caps = Capabilities(brightness = true))
        for (s in listOf(null, state(StateValue.Unknown))) {
            val m = ControlMapper.model(e, s, emptyList())
            assertFalse(m.on)
            assertEquals(ControlStatus.OK, m.status)
        }
    }

    @Test
    fun unavailable_state_marks_unavailable() {
        val m = ControlMapper.model(entity(Kind.SWITCH), state(StateValue.OnOff(true), available = false), emptyList())
        assertEquals(ControlStatus.UNAVAILABLE, m.status)
    }

    @Test
    fun commands_are_explicit_never_toggle() {
        val m = model()
        assertEquals(Command.TurnOn(), ControlMapper.command(m, ControlAction.SetOn(true)))
        assertEquals(Command.TurnOff, ControlMapper.command(m, ControlAction.SetOn(false)))
        assertEquals(Command.TurnOn(brightness = 128), ControlMapper.command(m, ControlAction.SetBrightness(50f)))
        assertEquals(Command.TurnOn(brightness = 255), ControlMapper.command(m, ControlAction.SetBrightness(100f)))
        assertEquals(Command.TurnOff, ControlMapper.command(m, ControlAction.SetBrightness(0f)))
        assertEquals(Command.TurnOff, ControlMapper.command(m, ControlAction.SetBrightness(0.1f)))
    }

    @Test
    fun failures_map_to_statuses() {
        assertEquals(ControlStatus.OFFLINE, ControlMapper.status(Failure.OFFLINE))
        assertEquals(ControlStatus.AUTH_FAILED, ControlMapper.status(Failure.AUTH_FAILED))
        assertEquals(ControlStatus.NOT_CONFIGURED, ControlMapper.status(Failure.NOT_CONFIGURED))
    }

    @Test
    fun a_lit_light_never_shows_zero_percent() {
        val e = entity(Kind.LIGHT, caps = Capabilities(brightness = true))
        assertEquals(1, ControlMapper.model(e, state(StateValue.Light(true, 1)), emptyList()).brightnessPercent)
        assertEquals(0, ControlMapper.model(e, state(StateValue.Light(false, 0)), emptyList()).brightnessPercent)
    }
}
