package com.gekkotron.nidoo.source.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Expands HA MQTT discovery abbreviations (table in [ABBREVIATIONS_JSON], spec §4.3). */
internal object Abbreviations {
    private val tables: Map<String, Map<String, String>> by lazy {
        Json.parseToJsonElement(ABBREVIATIONS_JSON).jsonObject.mapValues { (_, table) ->
            table.jsonObject.mapValues { it.value.jsonPrimitive.content }
        }
    }

    fun expand(raw: JsonObject): JsonObject {
        val main = tables.getValue("payload")
        return JsonObject(
            rename(raw, main).mapValues { (key, value) ->
                when (key) {
                    "device" -> (value as? JsonObject)?.let { rename(it, tables.getValue("device")) } ?: value
                    "origin" -> (value as? JsonObject)?.let { rename(it, tables.getValue("origin")) } ?: value
                    "availability" -> (value as? JsonArray)
                        ?.let { list -> JsonArray(list.map { item -> (item as? JsonObject)?.let { rename(it, main) } ?: item }) }
                        ?: value
                    "components" -> (value as? JsonObject)
                        ?.let { cmps -> JsonObject(cmps.mapValues { (_, c) -> (c as? JsonObject)?.let(::expand) ?: c }) }
                        ?: value
                    else -> value
                }
            },
        )
    }

    /** Full key names win over abbreviations when both are present. */
    private fun rename(o: JsonObject, table: Map<String, String>): JsonObject = JsonObject(
        buildMap {
            for ((k, v) in o) {
                val full = table[k] ?: k
                if (k == full || full !in this) put(full, v)
            }
        },
    )
}
