package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.climate.ClimateSetpoint
import com.gekkotron.nidoo.climate.ClimateZone
import com.gekkotron.nidoo.climate.ZoneOverride
import com.gekkotron.nidoo.model.Entity

/** Why Climat holds the room where it is, from the decision's `source`. */
enum class ClimatWhy { SCHEDULE, BOOST, HOLD, ECO, AWAY, HOLIDAY, EMPTY, SAFETY, IDLE, OTHER }

/** What the hub did with a boost or cancel: sent once per command (the sheet closes, or a snackbar says no). */
enum class ClimatResult { CONFIRMED, REFUSED }

/** A live boost: the room target and when it ends (Unix seconds). */
data class ClimatBoostUi(val target: Double, val until: Long)

/** The Climat banner of a room (spec §6). [min]/[max] bound the boost target. */
data class ClimatBannerUi(
    val zone: String,
    val reading: Double?,
    val target: Double?,
    val why: ClimatWhy,
    val reason: String,
    val boost: ClimatBoostUi?,
    /** End of a live whole-zone hold that is not a boost (mode only, or off). */
    val holdUntil: Long?,
    val canBoost: Boolean,
    val min: Double,
    val max: Double,
)

private const val DEFAULT_MIN_TEMP = 7.0 // ClimateMapper's defaults
private const val DEFAULT_MAX_TEMP = 35.0

/**
 * The banner for area [areaId]: the active Climat zone whose devices (the decision's, else the
 * settings' list) or room thermometer sit in that area. With several, the one with the most of
 * them there, then the smallest zone id. Null when no active zone covers the room.
 */
fun climatBannerFor(areaId: String, zones: Collection<ClimateZone>, entities: Map<String, Entity>, nowSec: Long): ClimatBannerUi? {
    fun devices(z: ClimateZone): Set<String> = z.decision?.devices?.takeIf { it.isNotEmpty() } ?: z.settings?.entities.orEmpty().toSet()
    fun inRoom(id: String) = entities[id]?.area == areaId
    val zone = zones
        .filter { it.settings?.state == "active" }
        .map { z -> z to (devices(z) + listOfNotNull(z.settings?.sensor)).count(::inRoom) }
        .filter { (_, matches) -> matches > 0 }
        .sortedWith(compareByDescending<Pair<ClimateZone, Int>> { it.second }.thenBy { it.first.id })
        .firstOrNull()?.first ?: return null

    val live = zone.overrides.filter { it.entity == null && it.until > nowSec }
    val boost = live.filter { it.setpoint is ClimateSetpoint.Temp }.maxByOrNull { it.until }
    val hold = live.filterNot { it.setpoint is ClimateSetpoint.Temp }.maxByOrNull(ZoneOverride::until)
    val decision = zone.decision
    val why = when (decision?.source) {
        "schedule" -> ClimatWhy.SCHEDULE
        "boost" -> ClimatWhy.BOOST
        // An older hub calls a boost an override; a mode-only hold also carries a temperature
        // (its mode's schedule), so the live override tells the two apart.
        "override" -> if (boost != null) ClimatWhy.BOOST else ClimatWhy.HOLD
        "eco" -> ClimatWhy.ECO
        "away" -> ClimatWhy.AWAY
        "holiday" -> ClimatWhy.HOLIDAY
        "empty" -> ClimatWhy.EMPTY
        "safety" -> ClimatWhy.SAFETY
        "idle" -> ClimatWhy.IDLE
        else -> ClimatWhy.OTHER
    }
    val caps = devices(zone).mapNotNull { entities[it]?.capabilities }
    val min = caps.maxOfOrNull { it.minTemp ?: DEFAULT_MIN_TEMP } ?: DEFAULT_MIN_TEMP
    val max = caps.minOfOrNull { it.maxTemp ?: DEFAULT_MAX_TEMP } ?: DEFAULT_MAX_TEMP
    return ClimatBannerUi(
        zone = zone.id,
        reading = decision?.reading,
        target = (decision?.setpoint as? ClimateSetpoint.Temp)?.celsius,
        why = why,
        reason = decision?.reason.orEmpty(),
        boost = boost?.let { ClimatBoostUi((it.setpoint as ClimateSetpoint.Temp).celsius, it.until) },
        holdUntil = hold?.until,
        canBoost = why != ClimatWhy.SAFETY,
        min = min,
        max = maxOf(min, max),
    )
}
