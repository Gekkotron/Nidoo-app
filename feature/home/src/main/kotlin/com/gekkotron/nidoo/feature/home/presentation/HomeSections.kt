package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.Kind

/** Home's by-type sections, in display order (rooms design §6.4). */
enum class HomeSection { LIGHTS, SHUTTERS_CLIMATE, PLUGS_MEDIA, OTHER, SENSORS }

data class SectionUi(
    val section: HomeSection,
    val cards: List<CardUi>,
    /** False when it is the only section: a lone label says nothing. */
    val showLabel: Boolean,
)

/** [strip] holds a room's read-only sensors, shown as pills above the [sections]. */
data class HomeLayout(val strip: List<CardUi>, val sections: List<SectionUi>)

object HomeSections {
    /** By the effective kind: a switch shown as a light is a light, and a group card carries its members' kind. */
    fun of(card: CardUi): HomeSection = when (card.kind) {
        Kind.LIGHT -> HomeSection.LIGHTS
        Kind.COVER, Kind.CLIMATE, Kind.WATER_HEATER -> HomeSection.SHUTTERS_CLIMATE
        Kind.SWITCH, Kind.MEDIA_PLAYER, Kind.VACUUM, Kind.BUTTON -> HomeSection.PLUGS_MEDIA
        Kind.SENSOR, Kind.BINARY_SENSOR -> HomeSection.SENSORS
        Kind.ALARM_PANEL, Kind.NUMBER, Kind.SELECT -> HomeSection.OTHER
    }

    /** A room: sensors go to [HomeLayout.strip]. */
    fun room(cards: List<CardUi>): HomeLayout {
        val (sensors, rest) = cards.partition { of(it) == HomeSection.SENSORS }
        return HomeLayout(sensors, sections(rest))
    }

    /** All devices: sensors are the last section, as tiles; no strip (a strip across rooms would lose the room). */
    fun allDevices(cards: List<CardUi>): HomeLayout = HomeLayout(emptyList(), sections(cards))

    /** Sections in enum order, each keeping the incoming order of its cards; empty ones dropped. */
    private fun sections(cards: List<CardUi>): List<SectionUi> {
        val grouped = cards.groupBy(::of)
        return HomeSection.entries.mapNotNull { section ->
            grouped[section]?.let { SectionUi(section, it, showLabel = grouped.size > 1) }
        }
    }
}
