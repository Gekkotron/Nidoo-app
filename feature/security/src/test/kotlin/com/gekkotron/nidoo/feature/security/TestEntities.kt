package com.gekkotron.nidoo.feature.security

import com.gekkotron.nidoo.model.AlarmMode
import com.gekkotron.nidoo.model.AlarmState
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.state.mqtt.MqttBindings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object NoBindings : Bindings

fun sensor(id: String, deviceClass: String?, name: String = id, area: String = UNASSIGNED_AREA, ui: UiOverride = UiOverride(), partOf: String? = null) =
    Entity(id, null, name, Kind.BINARY_SENSOR, deviceClass, null, null, Capabilities(), NoBindings, area, ui, partOf)

fun panel(id: String = "alarm", name: String = "Alarm", order: Int? = null, ui: UiOverride = UiOverride(order = order)) = Entity(
    id, null, name, Kind.ALARM_PANEL, null, null, null,
    Capabilities(alarmModes = setOf(AlarmMode.HOME, AlarmMode.AWAY), codeDisarmRequired = true), NoBindings, UNASSIGNED_AREA, ui,
)

fun on(id: String, on: Boolean, available: Boolean = true, stale: Boolean = false) =
    EntityState(id, StateValue.OnOff(on), available = available, stale = stale)

fun alarm(id: String, state: AlarmState, stale: Boolean = false) = EntityState(id, StateValue.Alarm(state), stale = stale)

/** A switch (or button) opted onto Security as an action. */
fun action(id: String, kind: Kind = Kind.SWITCH, name: String = id, area: String = UNASSIGNED_AREA, ui: UiOverride = UiOverride(securityAction = true)) =
    Entity(id, null, name, kind, null, null, null, Capabilities(), NoBindings, area, ui)

/** A sensor discovered over MQTT, so HistoryRefs.changeRef finds its topic and key. */
fun mqttSensor(id: String, deviceClass: String, topic: String = "zigbee2mqtt/$id", key: String = "contact") = Entity(
    id, null, id, Kind.BINARY_SENSOR, deviceClass, null, null, Capabilities(),
    MqttBindings("binary_sensor", JsonObject(mapOf("state_topic" to JsonPrimitive(topic), "value_template" to JsonPrimitive("{{ value_json.$key }}")))),
    UNASSIGNED_AREA,
)
