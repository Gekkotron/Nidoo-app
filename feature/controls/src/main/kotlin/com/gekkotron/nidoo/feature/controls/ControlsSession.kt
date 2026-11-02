package com.gekkotron.nidoo.feature.controls

import com.gekkotron.nidoo.feature.controls.model.ControlAction
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType
import com.gekkotron.nidoo.feature.widget.CatalogResult
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.SendResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.model.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ControlsSession(
    private val entities: WidgetEntities,
    /** A live view of the house while the app's own connection is up, else null. */
    private val live: () -> Flow<RefreshResult.Ok>?,
    private val scope: CoroutineScope,
) {
    private val known = MutableStateFlow<Map<String, ControlModel>>(emptyMap())
    private val updates = MutableSharedFlow<ControlModel>(extraBufferCapacity = 64)
    private val inFlight = HashMap<String, Job>()

    /** Per id: bumped by every [perform], so a refresh started before a tap never overwrites its result. */
    private val sends = ConcurrentHashMap<String, Long>()

    /** Per id: how many [watch] subscriptions currently show it; a tap on a watched tile is accepted before its first state. */
    private val watching = ConcurrentHashMap<String, Int>()

    suspend fun catalog(): List<ControlModel> {
        val result = entities.catalog() as? CatalogResult.Ok ?: return emptyList()
        return result.entities
            .filter(ControlMapper::eligible)
            .map { ControlMapper.model(it, null, result.areas) }
            .sortedWith(compareBy<ControlModel, String?>(nullsLast()) { it.zone }.thenBy { it.title })
    }

    fun watch(ids: Set<String>): Flow<ControlModel> = channelFlow {
        ids.forEach { id -> watching.merge(id, 1, Int::plus) }
        try {
            // Last model sent to this subscriber per id: identical states are not re-sent.
            val shown = ConcurrentHashMap<String, ControlModel>()
            suspend fun show(model: ControlModel) {
                remember(model)
                if (shown.put(model.id, model) != model) send(model)
            }

            known.value.filterKeys { it in ids }.values.forEach { show(it) }
            launch(start = CoroutineStart.UNDISPATCHED) {
                updates.collect { if (it.id in ids) show(it) }
            }
            val stamps = ids.associateWith { sends[it] ?: 0L }
            fun stale(id: String) = (sends[id] ?: 0L) != stamps[id] || busy(id)
            when (val result = entities.refresh(ids)) {
                is RefreshResult.Ok -> refreshed(ids, result).forEach { if (!stale(it.id)) show(it) }
                is RefreshResult.Failed -> {
                    val status = ControlMapper.status(result.reason)
                    val missing = ids.filter { it !in known.value }
                    val catalog = if (missing.isEmpty()) emptyMap() else catalogEntries()
                    for (id in ids) {
                        if (stale(id)) continue
                        val base = known.value[id] ?: catalog[id] ?: placeholder(id)
                        show(base.copy(status = status))
                    }
                }
            }
            live()?.collect { result -> refreshed(ids, result).forEach { if (!busy(it.id)) show(it) } }
            awaitCancellation()
        } finally {
            ids.forEach { id -> watching.computeIfPresent(id) { _, n -> (n - 1).takeIf { it > 0 } } }
        }
    }

    fun perform(id: String, action: ControlAction): Boolean {
        val model = known.value[id] ?: placeholder(id).takeIf { watching.containsKey(id) } ?: return false
        sends.merge(id, 1L, Long::plus)
        synchronized(inFlight) {
            inFlight.remove(id)?.cancel()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val outcome = when (val result = entities.send(id, ControlMapper.command(model, action))) {
                    is SendResult.Confirmed -> ControlMapper.model(result.entity, result.state, emptyList()).copy(zone = model.zone)
                    SendResult.NotConfirmed -> model.copy(status = ControlStatus.NO_RESPONSE)
                    is SendResult.Failed -> model.copy(status = ControlMapper.status(result.reason))
                }
                remember(outcome)
                updates.emit(outcome)
            }
            inFlight[id] = job
            // Finished sends leave the map; a newer job for the same id is never removed by an older one.
            job.invokeOnCompletion { synchronized(inFlight) { if (inFlight[id] === job) inFlight.remove(id) } }
            job.start()
        }
        return true
    }

    private fun busy(id: String): Boolean = synchronized(inFlight) { inFlight[id]?.isActive == true }

    private fun remember(model: ControlModel) = known.update { it + (model.id to model) }

    /** The catalog's eligible entities as models, fetched once per call. */
    private suspend fun catalogEntries(): Map<String, ControlModel> {
        val result = entities.catalog() as? CatalogResult.Ok ?: return emptyMap()
        return result.entities.filter(ControlMapper::eligible).associate { it.uniqueId to ControlMapper.model(it, null, result.areas) }
    }

    private fun refreshed(ids: Set<String>, result: RefreshResult.Ok): List<ControlModel> = ids.map { id ->
        val entity: Entity? = result.entities[id]
        val previous = known.value[id] ?: placeholder(id)
        when {
            entity != null && ControlMapper.eligible(entity) -> ControlMapper.model(entity, result.states[id], result.areas)
            result.complete -> previous.copy(status = ControlStatus.NOT_FOUND)
            else -> previous.copy(status = ControlStatus.UNAVAILABLE)
        }
    }

    /** A tile we know nothing about yet (no state, no catalog): its id as title, so it never spins forever. */
    private fun placeholder(id: String) = ControlModel(id, id, null, ControlType.LIGHT, false, false, null, ControlStatus.UNAVAILABLE)
}
