package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.HubGroup
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride

/** Folds the covers and the lights of each room into one group card (room groups spec §2–§3). */
object RoomGroups {
    val GROUP_KINDS = setOf(Kind.COVER, Kind.LIGHT)
    private const val MIN_MEMBERS = 2

    fun kindKey(kind: Kind): String = kind.name.lowercase()

    fun groupId(areaId: String, kind: Kind): String = "group:$areaId:${kindKey(kind)}"

    private fun candidate(areaId: String, kind: Kind) = kind in GROUP_KINDS && areaId != UNASSIGNED_AREA

    /** Ids of Home-visible covers and lights whose room has at least one other, grouped or not. */
    fun groupableIds(entities: List<Entity>): Set<String> = entities
        .filter { candidate(it.area, it.kind) }
        .groupBy { it.area to it.kind }
        .values.filter { it.size >= MIN_MEMBERS }
        .flatMapTo(mutableSetOf()) { group -> group.map { it.uniqueId } }

    /**
     * [cards] are already filtered and sorted; each group takes its first member's place. [groupFavorites]
     * (areaId → kind keys like "cover","light") marks the folded group as a Favorite so the chip picks it up.
     */
    fun fold(cards: List<CardUi>, areas: List<Area>, groupFavorites: Map<String, Set<String>> = emptyMap()): List<CardUi> {
        val members = cards
            .filter { candidate(it.areaId, it.kind) && !it.ui.ungrouped }
            .groupBy { it.areaId to it.kind }
            .filterValues { it.size >= MIN_MEMBERS }
        if (members.isEmpty()) return cards
        val memberOf = members.entries.flatMap { (key, list) -> list.map { it.id to key } }.toMap()
        val names = areas.associate { it.id to it.groupNames }
        val emitted = mutableSetOf<Pair<String, Kind>>()
        return buildList {
            for (card in cards) {
                val key = memberOf[card.id]
                if (key == null) { add(card); continue }
                if (emitted.add(key)) {
                    val favorite = kindKey(key.second) in (groupFavorites[key.first] ?: emptySet())
                    add(group(key.first, key.second, members.getValue(key), names[key.first]?.get(kindKey(key.second)), favorite))
                }
            }
        }
    }

    private fun group(areaId: String, kind: Kind, members: List<CardUi>, customName: String?, favorite: Boolean): CardUi {
        val first = members.first()
        val value = if (kind == Kind.COVER) coverValue(members) else LightGroupValue(members)
        return CardUi(
            id = groupId(areaId, kind),
            name = customName?.trim().orEmpty(),
            kind = kind,
            deviceClass = null,
            areaName = first.areaName,
            value = value,
            state = StateValue.Unknown,
            capabilities = Capabilities(
                position = members.any { it.capabilities.position },
                brightness = members.any { it.capabilities.brightness },
            ),
            unit = null,
            on = members.any { it.on },
            wide = true,
            available = members.any { it.available },
            pending = members.any { it.pending },
            stale = members.any { it.stale },
            tap = TapAction.OPEN_SHEET,
            hasSheet = true,
            hasHistory = false,
            areaId = areaId,
            ui = UiOverride(favorite = favorite),
            hidden = false,
            members = members,
        )
    }

    /**
     * Hub groups spec §2.3: swaps a hub group's tile for a wide group card built from its
     * (would-be visible) members, and removes those members from [cards] when the hub asks to hide
     * them (`hideMembers`). Capabilities are the hub entity's own: the hub intersects member
     * capabilities when it builds the discovery config. Groups whose entity is absent, whose kind
     * Home cannot render as a group (covers and lights only), or that have no renderable member on
     * Home are left untouched.
     */
    fun foldHubGroups(cards: List<CardUi>, hubGroups: Map<String, HubGroup>): List<CardUi> {
        if (hubGroups.isEmpty()) return cards
        val byId = cards.associateBy { it.id }
        val plan = hubGroups.values.mapNotNull { spec ->
            if (spec.kind !in GROUP_KINDS) return@mapNotNull null
            val groupCard = byId[groupEntityId(spec.id)] ?: return@mapNotNull null
            val members = spec.members.mapNotNull { byId[it] }
            if (members.isEmpty()) return@mapNotNull null
            val area = members.map { it.areaId }.distinct().singleOrNull() ?: UNASSIGNED_AREA
            val folded = group(area, spec.kind, members, spec.name, favorite = groupCard.ui.favorite)
                .copy(id = groupCard.id, isHubGroup = true, ui = groupCard.ui, capabilities = groupCard.capabilities)
            val hidden = if (spec.hideMembers) members.map { it.id }.toSet() else emptySet()
            Triple(groupCard.id, hidden, folded)
        }
        if (plan.isEmpty()) return cards
        val replacements = plan.associate { it.first to it.third }
        val toRemove = plan.flatMap { it.second }.toSet()
        return cards.mapNotNull { card ->
            when {
                card.id in replacements -> replacements[card.id]
                card.id in toRemove -> null
                else -> card
            }
        }
    }

    /** Unique id of the hub group entity for group id [id] (`nidoo/cover/group/<id>/config`'s `unique_id`). */
    fun groupEntityId(id: String): String = "group_$id"

    private fun coverValue(members: List<CardUi>): CardValue.CoverGroup {
        val states = members.map { (it.state as? StateValue.Cover)?.state }
        val moving = when {
            CoverState.OPENING in states -> CoverState.OPENING
            CoverState.CLOSING in states -> CoverState.CLOSING
            else -> null
        }
        val positions = members.map { m -> (m.state as? StateValue.Cover)?.position?.coerceIn(0, 100) ?: if (m.on) 100 else 0 }
        return CardValue.CoverGroup(open = members.count { it.on }, total = members.size, moving = moving, positions = positions)
    }

    @Suppress("FunctionName")
    private fun LightGroupValue(members: List<CardUi>) = CardValue.LightGroup(onCount = members.count { it.on }, total = members.size)
}
