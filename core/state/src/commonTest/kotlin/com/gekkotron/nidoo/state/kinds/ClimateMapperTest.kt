package com.gekkotron.nidoo.state.kinds

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.StateValue.Climate
import com.gekkotron.nidoo.model.StateValue.Unknown
import com.gekkotron.nidoo.state.bindings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClimateMapperTest {
    // The Jeedom Daikin example from spec §3.B: current and target share one topic.
    private val daikin = bindings(
        "climate",
        """{"current_temperature_topic":"jeedom/data/rooms/living/climatisation",
           "current_temperature_template":"{{ value_json['température_int.'] }}",
           "temperature_state_topic":"jeedom/data/rooms/living/climatisation",
           "temperature_state_template":"{{ value_json.consigne }}",
           "temperature_command_topic":"jeedom/data/rooms/living/climatisation/set",
           "mode_state_topic":"m","mode_command_topic":"m/set",
           "modes":["off","cool","heat","auto","fan_only"]}""",
    )

    private fun publishes(topic: String, payload: String) = Encoded.Publish(listOf(Outgoing(topic, payload, false, 0)))

    @Test
    fun `shared topic fills current and target`() {
        assertEquals(
            Decoded.State(Climate(null, 21.0, 22.5)),
            ClimateMapper.decode(daikin, "jeedom/data/rooms/living/climatisation", """{"température_int.":22.5,"consigne":21}""", Unknown),
        )
    }

    @Test
    fun `mode must be one of the modes`() {
        assertEquals(Decoded.State(Climate("heat", 21.0, null)), ClimateMapper.decode(daikin, "m", "heat", Climate(null, 21.0, null)))
        assertEquals(Decoded.Ignored, ClimateMapper.decode(daikin, "m", "turbo", Unknown))
    }

    @Test
    fun `setpoint commands are formatted and range-checked`() {
        assertEquals(publishes("jeedom/data/rooms/living/climatisation/set", "20.5"), ClimateMapper.encode(daikin, Command.SetTemperature(20.5), Unknown))
        assertEquals(publishes("jeedom/data/rooms/living/climatisation/set", "21"), ClimateMapper.encode(daikin, Command.SetTemperature(21.0), Unknown))
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(daikin, Command.SetTemperature(40.0), Unknown))
    }

    private val powered = bindings(
        "climate",
        """{"mode_state_topic":"m","mode_command_topic":"m/set","power_command_topic":"p/set","modes":["off","cool","heat","auto"]}""",
    )

    @Test
    fun `power topic turns on and off without a mode`() {
        assertEquals(publishes("p/set", "ON"), ClimateMapper.encode(powered, Command.TurnOn(), Unknown))
        assertEquals(publishes("p/set", "OFF"), ClimateMapper.encode(powered, Command.TurnOff, Unknown))
        assertEquals(setOf("m"), ClimateMapper.feedbackTopics(powered, Command.TurnOn()))
        assertEquals(setOf("m"), ClimateMapper.feedbackTopics(powered, Command.TurnOff))
        assertEquals(null, ClimateMapper.predict(Command.TurnOn(), Climate("off", 20.0, null)))
        assertEquals(Climate("off", 20.0, null), ClimateMapper.predict(Command.TurnOff, Climate("heat", 20.0, null)))
    }

    @Test
    fun `power topic honours custom payloads`() {
        val custom = bindings(
            "climate",
            """{"mode_command_topic":"m/set","power_command_topic":"p/set","payload_on":"1","payload_off":"0"}""",
        )
        assertEquals(publishes("p/set", "1"), ClimateMapper.encode(custom, Command.TurnOn(), Unknown))
        assertEquals(publishes("p/set", "0"), ClimateMapper.encode(custom, Command.TurnOff, Unknown))
    }

    @Test
    fun `turn on without power topic is not supported`() {
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(daikin, Command.TurnOn(), Unknown))
    }

    @Test
    fun `mode commands`() {
        assertEquals(publishes("m/set", "cool"), ClimateMapper.encode(daikin, Command.SetHvacMode("cool"), Unknown))
        assertEquals(publishes("m/set", "off"), ClimateMapper.encode(daikin, Command.TurnOff, Unknown))
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(daikin, Command.SetHvacMode("dry"), Unknown))
        val noModeTopic = bindings("climate", """{"temperature_command_topic":"t"}""")
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(noModeTopic, Command.SetHvacMode("heat"), Unknown))
    }

    @Test
    fun `prediction and topics`() {
        assertEquals(Climate("off", 21.0, 22.5), ClimateMapper.predict(Command.SetHvacMode("off"), Climate("cool", 21.0, 22.5)))
        assertEquals(Climate(null, 19.0, null), ClimateMapper.predict(Command.SetTemperature(19.0), Unknown))
        assertEquals(setOf("jeedom/data/rooms/living/climatisation", "m"), ClimateMapper.stateTopics(daikin))
        assertEquals(ClimateMapper.DEFAULT_MODES, ClimateMapper.modes(bindings("climate", "{}")))
    }

    private val fan = bindings(
        "climate",
        """{"fan_modes":["auto","silence","1","3"],"fan_mode_state_topic":"s",
           "fan_mode_state_template":"{{ value_json.fan_mode }}","fan_mode_command_topic":"f/set"}""",
    )

    @Test
    fun `fan mode is decoded only when listed`() {
        assertEquals(Decoded.State(Climate(null, 21.0, null, "3")), ClimateMapper.decode(fan, "s", """{"fan_mode":"3"}""", Climate(null, 21.0, null)))
        assertEquals(Decoded.Ignored, ClimateMapper.decode(fan, "s", """{"fan_mode":"5"}""", Unknown))
        assertEquals(setOf("s"), ClimateMapper.stateTopics(fan))
    }

    @Test
    fun `fan mode commands`() {
        assertEquals(publishes("f/set", "3"), ClimateMapper.encode(fan, Command.SetFanMode("3"), Unknown))
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(fan, Command.SetFanMode("5"), Unknown))
        assertIs<Encoded.NotSupported>(ClimateMapper.encode(daikin, Command.SetFanMode("auto"), Unknown))
        assertEquals(Climate(null, null, null, "silence"), ClimateMapper.predict(Command.SetFanMode("silence"), Unknown))
    }
}
