package com.gekkotron.nidoo.climate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A Climat setpoint: a temperature in °C, or off. The hub sends a number or `"off"`. */
sealed interface ClimateSetpoint {
    data class Temp(val celsius: Double) : ClimateSetpoint

    data object Off : ClimateSetpoint
}

/** The parts of a zone's `settings` document the app reads. `entities` null means "every climate device of the area". */
data class ZoneSettings(val state: String, val entities: List<String>?, val sensor: String?)

/** The parts of a zone's `decision` document the app reads. [devices] are the keys of the hub's `entities`. */
data class ZoneDecision(
    val mode: String?,
    val setpoint: ClimateSetpoint?,
    val source: String,
    val reason: String,
    val reading: Double?,
    val at: Long,
    val devices: Set<String>,
)

/** One entry of a zone's `override` document; [entity] null is a whole-zone override (a boost or a hold). */
data class ZoneOverride(
    val entity: String?,
    val setpoint: ClimateSetpoint?,
    val mode: String?,
    val until: Long,
    val origin: String,
)

/** What the app knows of one Climat zone; any document may still be missing. */
data class ClimateZone(
    val id: String,
    val settings: ZoneSettings?,
    val decision: ZoneDecision?,
    val overrides: List<ZoneOverride>,
)

/** The retained zone documents the app reads. */
enum class ClimateDoc { SETTINGS, DECISION, OVERRIDE }

/** Decodes the hub's zone documents (Oikos-os `nidoo-climate/src/doc.rs`). Throws on a malformed payload. */
object ClimateJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun settings(payload: String): ZoneSettings {
        val o = json.parseToJsonElement(payload).jsonObject
        return ZoneSettings(
            state = o.string("state") ?: error("settings without a state"),
            entities = (o["entities"] as? JsonArray)?.map { it.jsonPrimitive.content },
            sensor = o.string("sensor"),
        )
    }

    fun decision(payload: String): ZoneDecision {
        val o = json.parseToJsonElement(payload).jsonObject
        return ZoneDecision(
            mode = o.string("mode"),
            setpoint = setpoint(o["setpoint"]),
            source = o.string("source") ?: error("decision without a source"),
            reason = o.string("reason").orEmpty(),
            reading = o.primitive("reading")?.doubleOrNull,
            at = o.primitive("at")?.longOrNull ?: 0L,
            devices = (o["entities"] as? JsonObject)?.keys.orEmpty(),
        )
    }

    fun overrides(payload: String): List<ZoneOverride> =
        json.parseToJsonElement(payload).jsonArray.map { e ->
            val o = e.jsonObject
            ZoneOverride(
                entity = o.string("entity"),
                setpoint = setpoint(o["setpoint"]),
                mode = o.string("mode"),
                until = o.primitive("until")?.longOrNull ?: error("override without until"),
                origin = o.string("origin").orEmpty(),
            )
        }

    private fun setpoint(e: JsonElement?): ClimateSetpoint? {
        val p = e as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (p.isString) return if (p.content == "off") ClimateSetpoint.Off else error("unknown setpoint ${p.content}")
        return ClimateSetpoint.Temp(p.doubleOrNull ?: error("setpoint is not a number"))
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull
}
