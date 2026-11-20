package com.gekkotron.nidoo.flows

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

const val WAKE_UP_LABEL = "wake-up"

/** A wake-up alarm's time. [days] is 0 = Sunday .. 6 = Saturday, sorted, distinct and never empty. */
data class WakeUpTime(val hour: Int, val minute: Int, val days: List<Int>)

/** The slice of a Nidoo flow (automation) the UI reads; everything else is ignored. */
@Serializable
data class FlowDto(
    val id: String,
    val name: String,
    val area: String? = null,
    val enabled: Boolean = true,
    val labels: List<String> = emptyList(),
    /** Kept raw so a malformed `wake_up` cannot make the whole flow fail to decode. */
    @SerialName("wake_up") val wakeUp: JsonElement? = null,
)

val FlowDto.isWakeUp: Boolean get() = WAKE_UP_LABEL in labels || wakeUp != null

/** The alarm's time, or null when `wake_up` is missing, malformed, out of range or has no day. */
fun FlowDto.wakeUpTime(): WakeUpTime? {
    val obj = wakeUp as? JsonObject ?: return null
    val hour = (obj["hour"] as? JsonPrimitive)?.intOrNull ?: return null
    val minute = (obj["minute"] as? JsonPrimitive)?.intOrNull ?: return null
    val rawDays = obj["days"] as? JsonArray ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    val days = rawDays.map { (it as? JsonPrimitive)?.intOrNull ?: return null }
    if (days.isEmpty() || days.any { it !in 0..6 }) return null
    return WakeUpTime(hour, minute, days.distinct().sorted())
}

val flowsJson = Json { ignoreUnknownKeys = true; isLenient = false }
