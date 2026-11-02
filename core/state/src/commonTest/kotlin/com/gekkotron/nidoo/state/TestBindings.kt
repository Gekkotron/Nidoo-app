package com.gekkotron.nidoo.state

import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.state.mqtt.MqttBindings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

fun bindings(component: String, json: String) = MqttBindings(component, Json.parseToJsonElement(json).jsonObject)

fun testEntity(id: String, kind: Kind, component: String, json: String) = Entity(
    uniqueId = id, deviceId = null, name = id, kind = kind, deviceClass = null, unit = null, icon = null,
    capabilities = Capabilities(), bindings = bindings(component, json),
)
