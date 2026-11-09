package com.gekkotron.nidoo.climate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The hub's Climat zones (retained MQTT, `nidoo/climate/zone/<zone>/<doc>`). Read-only: the app
 * changes a zone only through the hold command. A deleted `settings` document removes the zone.
 *
 * The hub deletes a retained document rather than emptying it (an override list that became
 * empty, a zone switched off), and a clean session replays nothing for it. So after each link-up
 * ([markLinkUp]) a document the broker does not replay within [confirmAfter] is dropped, as the
 * dashboards are. Without [scope] that reconciliation is off (tests, previews).
 */
class ClimateRepository(
    private val scope: CoroutineScope? = null,
    private val confirmAfter: Duration = 5.seconds,
    private val log: (String) -> Unit = {},
) : ClimateSink {

    private val _zones = MutableStateFlow<Map<String, ClimateZone>>(emptyMap())
    val zones: StateFlow<Map<String, ClimateZone>> = _zones.asStateFlow()

    /** Documents held before the last link-up that the broker has not replayed since. */
    private val unconfirmed = MutableStateFlow<Set<Pair<String, ClimateDoc>>>(emptySet())
    private val confirmTimer = MutableStateFlow<Job?>(null)

    override fun onRetained(zone: String, doc: ClimateDoc, payload: String?) {
        // Replayed, even if malformed: the broker still holds it.
        unconfirmed.update { it - (zone to doc) }
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

    override fun markLinkUp() {
        val scope = scope ?: return
        unconfirmed.value = _zones.value.values.flatMapTo(mutableSetOf()) { z ->
            listOfNotNull(
                (z.id to ClimateDoc.SETTINGS).takeIf { z.settings != null },
                (z.id to ClimateDoc.DECISION).takeIf { z.decision != null },
                (z.id to ClimateDoc.OVERRIDE).takeIf { z.overrides.isNotEmpty() },
            )
        }
        val confirm = scope.launch(start = CoroutineStart.LAZY) {
            delay(confirmAfter)
            val gone = unconfirmed.getAndUpdate { emptySet() }
            if (gone.isNotEmpty()) _zones.update { all -> forget(all, gone) }
        }
        confirmTimer.getAndUpdate { confirm }?.cancel()
        confirm.start()
    }

    override fun clear() {
        confirmTimer.getAndUpdate { null }?.cancel()
        unconfirmed.value = emptySet()
        _zones.value = emptyMap()
    }

    private fun forget(all: Map<String, ClimateZone>, gone: Set<Pair<String, ClimateDoc>>): Map<String, ClimateZone> {
        var out = all
        for ((zone, doc) in gone) {
            val z = out[zone] ?: continue
            out = when (doc) {
                ClimateDoc.SETTINGS -> out - zone
                ClimateDoc.DECISION -> out + (zone to z.copy(decision = null))
                ClimateDoc.OVERRIDE -> out + (zone to z.copy(overrides = emptyList()))
            }
        }
        return out
    }
}
