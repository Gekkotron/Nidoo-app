package com.gekkotron.nidoo.climate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The hub's Climat zones (retained MQTT, `nidoo/climate/zone/<zone>/<doc>`). Read-only: the app
 * changes a zone only through the hold command. A deleted `settings` document removes the zone.
 */
class ClimateRepository(private val log: (String) -> Unit = {}) : ClimateSink {

    private val _zones = MutableStateFlow<Map<String, ClimateZone>>(emptyMap())
    val zones: StateFlow<Map<String, ClimateZone>> = _zones.asStateFlow()

    override fun onRetained(zone: String, doc: ClimateDoc, payload: String?) {
        val empty = payload.isNullOrEmpty()
        if (doc == ClimateDoc.SETTINGS && empty) {
            _zones.update { it - zone }
            return
        }
        val change: (ClimateZone) -> ClimateZone = try {
            when (doc) {
                ClimateDoc.SETTINGS -> ClimateJson.settings(payload!!).let { s -> { z -> z.copy(settings = s) } }
                ClimateDoc.DECISION -> (if (empty) null else ClimateJson.decision(payload!!)).let { d -> { z -> z.copy(decision = d) } }
                ClimateDoc.OVERRIDE -> (if (empty) emptyList() else ClimateJson.overrides(payload!!)).let { o -> { z -> z.copy(overrides = o) } }
            }
        } catch (e: Exception) {
            log("dropping malformed climate $doc for '$zone': ${e.message}")
            return
        }
        _zones.update { all ->
            val current = all[zone] ?: ClimateZone(zone, settings = null, decision = null, overrides = emptyList())
            all + (zone to change(current))
        }
    }

    override fun clear() {
        _zones.value = emptyMap()
    }
}
