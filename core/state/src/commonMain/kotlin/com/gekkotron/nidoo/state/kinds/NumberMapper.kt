package com.gekkotron.nidoo.state.kinds

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.state.mqtt.MqttBindings
import com.gekkotron.nidoo.state.template.asDouble
import com.gekkotron.nidoo.state.template.formatNumber

object NumberMapper : KindMapper {
    const val DEFAULT_MIN = 1.0
    const val DEFAULT_MAX = 100.0
    const val DEFAULT_STEP = 1.0

    override fun stateTopics(b: MqttBindings): Set<String> = setOfNotNull(b.str("state_topic"))

    override fun decode(b: MqttBindings, topic: String, payload: String, previous: StateValue): Decoded =
        renderIncoming(b, "value_template", payload).decodeWith { v ->
            v.asDouble()?.let { Decoded.State(StateValue.Numeric(it)) } ?: Decoded.Ignored
        }

    override fun encode(b: MqttBindings, command: Command, previous: StateValue): Encoded {
        if (command !is Command.SetNumber) return Encoded.NotSupported("number cannot $command")
        val min = b.double("min") ?: DEFAULT_MIN
        val max = b.double("max") ?: DEFAULT_MAX
        if (command.value !in min..max) return Encoded.NotSupported("value ${command.value} outside $min..$max")
        return publish(b, "command_topic", "command_template", formatNumber(command.value))
    }

    override fun predict(command: Command, previous: StateValue): StateValue? =
        (command as? Command.SetNumber)?.let { StateValue.Numeric(it.value) }
}
