package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.HUB_GROUP_DEVICE_ID
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.LightOnPreset
import com.gekkotron.nidoo.model.Placement
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import java.text.Normalizer
import java.util.Locale

/** A room card's icon (rooms design §6.2): the area's own key when known, else guessed from its name. */
enum class RoomIcon(private vararg val keywords: String) {
    // Order matters: the first keyword found in the name wins ("salle de bain" before anything shorter).
    BATHROOM("salle de bain", "salle d'eau", "bath", "douche", "shower"),
    TOILET("wc", "toilet"),
    BEDROOM("chambre", "bedroom"),
    LIVING_ROOM("salon", "sejour", "living", "lounge"),
    KITCHEN("cuisine", "kitchen"),
    OFFICE("bureau", "office", "study"),
    GARAGE("garage"),
    GARDEN("jardin", "garden", "terrasse", "terrace"),
    ENTRANCE("entree", "entrance", "hall"),
    LAUNDRY("buanderie", "laundry"),
    GENERIC,
    ;

    val key: String get() = name.lowercase()

    companion object {
        private val MARKS = Regex("\\p{Mn}+")

        fun of(areaIcon: String?, areaName: String?): RoomIcon {
            entries.firstOrNull { it != GENERIC && it.key == areaIcon }?.let { return it }
            val name = areaName?.let { Normalizer.normalize(it.lowercase(), Normalizer.Form.NFD).replace(MARKS, "") } ?: return GENERIC
            return entries.firstOrNull { icon -> icon.keywords.any { it in name } } ?: GENERIC
        }
    }
}

/** One device a whole-room button acts on. For a shutter, [on] means "not closed" (rooms design §6.3). */
data class RoomTarget(
    val id: String,
    val name: String,
    val available: Boolean,
    val stale: Boolean,
    val on: Boolean,
    /** OPENING or CLOSING while a shutter moves; null otherwise and for lights. */
    val moving: CoverState? = null,
    /** What turning this light on sends (its preset, or a bare on). */
    val onCommand: Command.TurnOn = Command.TurnOn(),
)

data class RoomCardUi(
    val areaId: String,
    /** Null = Unassigned, localized by the UI. */
    val name: String?,
    val icon: RoomIcon,
    val temperature: CardValue.Measurement?,
    val humidity: CardValue.Measurement?,
    val lights: List<RoomTarget>,
    val shutters: List<RoomTarget>,
    /** Devices Home shows in this room. */
    val deviceCount: Int,
    /** False for Unassigned: a card without whole-room buttons. */
    val buttons: Boolean,
) {
    val lightsOn: Int get() = lights.count { it.available && it.on }
    val shuttersOpen: Int get() = shutters.count { it.available && it.on }
    val shuttersMoving: CoverState?
        get() {
            val moving = shutters.filter { it.available }.mapNotNull { it.moving }
            return when {
                CoverState.OPENING in moving -> CoverState.OPENING
                CoverState.CLOSING in moving -> CoverState.CLOSING
                else -> null
            }
        }
}

/** A light with a saved on-state preset turns on at that colour/brightness; otherwise plain on.
 *  A customised [UiOverride.lightOnBrightness] replaces the preset's baked brightness. */
internal fun lightOnCommand(ui: UiOverride, capabilities: Capabilities): Command.TurnOn {
    val preset = LightOnPreset.of(ui.lightOnPreset) ?: return Command.TurnOn()
    val color = preset.color.takeIf { capabilities.colorModes.isNotEmpty() }
    val brightness = (ui.lightOnBrightness ?: preset.brightness).takeIf { capabilities.brightness }
    return Command.TurnOn(brightness = brightness, color = color)
}

/** The Rooms view's cards (rooms design §6.2), from the entities Home shows. */
object RoomCards {
    /**
     * [entities] are filtered here to what a room shows: standalone and on Home. A hub group entity
     * counts as one of the room's devices (it is a card there) but is never a target: its members
     * are. Rooms come in [areas] order, Unassigned last.
     */
    fun build(
        entities: Collection<Entity>,
        states: Map<String, EntityState>,
        areas: List<Area>,
        names: Map<String, String>,
        locale: Locale,
    ): List<RoomCardUi> {
        val byArea = entities
            .filter { it.partOf == null && Placement.onHome(it.kind, it.deviceClass, it.ui) }
            .sortedWith(compareBy({ it.ui.order ?: Int.MAX_VALUE }, { (names[it.uniqueId] ?: it.name).lowercase(locale) }, { it.uniqueId }))
            .groupBy { it.area }
        if (byArea.isEmpty()) return emptyList()
        val known = areas.filter { it.id != UNASSIGNED_AREA }
        val listed = known.mapNotNull { area -> byArea[area.id]?.let { room(area.id, area.name, area.icon, it, states, names, locale) } }
        // Entities whose area the areas document does not list still belong somewhere: Unassigned.
        val knownIds = known.mapTo(mutableSetOf()) { it.id }
        val rest = byArea.filterKeys { it !in knownIds }.values.flatten()
        return if (rest.isEmpty()) listed else listed + room(UNASSIGNED_AREA, null, null, rest, states, names, locale)
    }

    private fun room(
        id: String,
        name: String?,
        icon: String?,
        entities: List<Entity>,
        states: Map<String, EntityState>,
        names: Map<String, String>,
        locale: Locale,
    ): RoomCardUi {
        fun target(e: Entity): RoomTarget {
            val state = states[e.uniqueId]
            val cover = state?.value as? StateValue.Cover
            val on = when (val v = state?.value) {
                is StateValue.Light -> v.on
                is StateValue.OnOff -> v.on
                // Closed at position 0, or CLOSED when it reports no position; anything else is open.
                is StateValue.Cover -> if (v.position != null) v.position != 0 else v.state != CoverState.CLOSED
                else -> e.kind == Kind.COVER
            }
            return RoomTarget(
                id = e.uniqueId,
                name = names[e.uniqueId] ?: e.name,
                available = state?.available ?: true,
                stale = state?.stale ?: false,
                on = on,
                moving = cover?.state?.takeIf { it == CoverState.OPENING || it == CoverState.CLOSING },
                onCommand = if (e.kind == Kind.LIGHT) lightOnCommand(e.ui, e.capabilities) else Command.TurnOn(),
            )
        }
        fun measurement(deviceClass: String): CardValue.Measurement? = entities.firstNotNullOfOrNull { e ->
            if (e.kind != Kind.SENSOR || e.deviceClass != deviceClass) return@firstNotNullOfOrNull null
            val state = states[e.uniqueId]?.takeIf { it.available } ?: return@firstNotNullOfOrNull null
            CardFormatter.format(e, state.value, locale) as? CardValue.Measurement
        }
        return RoomCardUi(
            areaId = id,
            name = name,
            icon = RoomIcon.of(icon, name),
            temperature = measurement("temperature"),
            humidity = measurement("humidity"),
            lights = entities.filter { it.kind == Kind.LIGHT && it.deviceId != HUB_GROUP_DEVICE_ID }.map(::target),
            shutters = entities.filter { it.kind == Kind.COVER && it.deviceId != HUB_GROUP_DEVICE_ID }.map(::target),
            deviceCount = entities.size,
            buttons = id != UNASSIGNED_AREA,
        )
    }
}
