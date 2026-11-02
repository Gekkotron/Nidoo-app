package com.gekkotron.nidoo.voice

import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UiOverride

private object NoBindings : Bindings

fun entity(
    id: String,
    name: String,
    kind: Kind,
    area: String = "unassigned",
    deviceClass: String? = null,
    brightness: Boolean = false,
    position: Boolean = false,
    hidden: Boolean = false,
    partOf: String? = null,
    override: UiOverride = UiOverride(),
    unit: String? = null,
): Entity = Entity(
    uniqueId = id,
    deviceId = null,
    name = name,
    kind = kind,
    deviceClass = deviceClass,
    unit = unit,
    icon = null,
    capabilities = Capabilities(brightness = brightness, position = position),
    bindings = NoBindings,
    area = area,
    ui = override.copy(hidden = hidden),
    partOf = partOf,
)

fun area(id: String, name: String): Area = Area(id = id, name = name)
