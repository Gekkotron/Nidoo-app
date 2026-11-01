package com.gekkotron.nidoo.feature.settings

import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UNASSIGNED_AREA

object NoBindings : Bindings

fun testEntity(id: String, kind: Kind, area: String = UNASSIGNED_AREA): Entity =
    Entity(id, null, id, kind, null, null, null, Capabilities(), NoBindings, area)
