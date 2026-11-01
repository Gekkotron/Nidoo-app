package com.gekkotron.nidoo.feature.home

import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride

object NoBindings : Bindings

fun testEntity(
    id: String,
    kind: Kind,
    name: String = id,
    area: String = UNASSIGNED_AREA,
    deviceClass: String? = null,
    unit: String? = null,
    ui: UiOverride = UiOverride(),
    capabilities: Capabilities = Capabilities(),
    deviceId: String? = null,
    partOf: String? = null,
) = Entity(id, deviceId, name, kind, deviceClass, unit, null, capabilities, NoBindings, area, ui, partOf)
