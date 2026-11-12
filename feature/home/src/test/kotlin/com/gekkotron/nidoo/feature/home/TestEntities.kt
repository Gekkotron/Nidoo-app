package com.gekkotron.nidoo.feature.home

import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.HUB_GROUP_DEVICE_ID
import com.gekkotron.nidoo.model.HubGroup
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.util.Locale

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

/** The hub group card (`group_$id`) the hub would send over [members], which stay in [area]. */
fun hubGroupCard(
    vararg members: Entity,
    kind: Kind,
    states: Map<String, EntityState> = emptyMap(),
    area: String = "living",
    id: String = "g",
    name: String = "",
    hideMembers: Boolean = true,
    capabilities: Capabilities = Capabilities(position = true, brightness = true),
): CardUi {
    val entity = testEntity("group_$id", kind, name, area = area, deviceId = HUB_GROUP_DEVICE_ID, capabilities = capabilities)
    val spec = HubGroup(id, kind, name, members.map { it.uniqueId }, hideMembers)
    return HomeUiBuilder.build(
        (members.toList() + entity).associateBy { it.uniqueId }, states, listOf(Area(area, "Living room")),
        RoomFilter.All, LinkState.Connected(TransportMode.DEMO), Locale.US, hubGroups = mapOf(id to spec),
    ).cards.single { it.isHubGroup }
}

/** [entities] plus a hub group entity (`group_cover_living`, `group_light_living`) over each kind's living-room entities. */
class HubHouse(val entities: Map<String, Entity>, val hubGroups: Map<String, HubGroup>)

fun withLivingHubGroups(entities: List<Entity>): HubHouse {
    val groups = listOf(Kind.COVER, Kind.LIGHT).mapNotNull { kind ->
        val members = entities.filter { it.kind == kind && it.area == "living" }
        if (members.isEmpty()) return@mapNotNull null
        val id = "${kind.name.lowercase()}_living"
        val capabilities = Capabilities(position = members.any { it.capabilities.position }, brightness = members.any { it.capabilities.brightness })
        testEntity("group_$id", kind, id, area = "living", deviceId = HUB_GROUP_DEVICE_ID, capabilities = capabilities) to
            HubGroup(id, kind, "", members.map { it.uniqueId })
    }
    return HubHouse((entities + groups.map { it.first }).associateBy { it.uniqueId }, groups.associate { it.second.id to it.second })
}
