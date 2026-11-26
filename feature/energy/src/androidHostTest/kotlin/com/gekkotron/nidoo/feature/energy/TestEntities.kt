package com.gekkotron.nidoo.feature.energy

import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.EnergyRole
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.state.mqtt.MqttBindings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object NoBindings : Bindings

fun energyEntity(
    id: String,
    role: EnergyRole?,
    unit: String? = "W",
    name: String = id,
    invert: Boolean = false,
    order: Int? = null,
    icon: String? = null,
) = Entity(
    id, null, name, Kind.SENSOR, "power", unit, null, Capabilities(), NoBindings, UNASSIGNED_AREA,
    UiOverride(energyRole = role, energyInvert = invert, order = order, energyIcon = icon),
)

fun numeric(id: String, value: Double, stale: Boolean = false, available: Boolean = true) =
    EntityState(id, StateValue.Numeric(value), available = available, stale = stale)

fun unknown(id: String) = EntityState(id, StateValue.Unknown)

/** Bound like a Z2M sensor on `zigbee2mqtt/<id>`, reading [key] from its payload, so history can find it. */
fun Entity.recorded(key: String) = copy(
    bindings = MqttBindings(
        "sensor",
        buildJsonObject {
            put("state_topic", JsonPrimitive("zigbee2mqtt/$uniqueId"))
            put("value_template", JsonPrimitive("{{ value_json.$key }}"))
        },
    ),
)
