package com.gekkotron.nidoo.feature.controls

import com.gekkotron.nidoo.feature.controls.model.ControlAction
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType
import com.gekkotron.nidoo.feature.widget.CatalogResult
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.SendResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ControlsSession(
    private val entities: WidgetEntities,
    /** A live view of the house while the app's own connection is up, else null. */
    private val live: () -> Flow<RefreshResult.Ok>?,
    private val scope: CoroutineScope,
) {
    private val known = MutableStateFlow<Map<String, ControlModel>>(emptyMap())
    private val updates = MutableSharedFlow<ControlModel>(extraBufferCapacity = 64)
    private val inFlight = HashMap<String, Job>()

    suspend fun catalog(): List<ControlModel> {
        val result = entities.catalog() as? CatalogResult.Ok ?: return emptyList()
        return result.entities
            .filter(ControlMapper::eligible)
            .map { ControlMapper.model(it, null, result.areas) }
            .sortedWith(compareBy<ControlModel, String?>(nullsLast()) { it.zone }.thenBy { it.title })
    }

    fun watch(ids: Set<String>): Flow<ControlModel> = channelFlow {
        known.value.filterKeys { it in ids }.values.forEach { send(it) }
        launch(start = CoroutineStart.UNDISPATCHED) {
            updates.collect { if (it.id in ids) send(it) }
        }
        when (val result = entities.refresh(ids)) {
            is RefreshResult.Ok -> emitAll(ids, result)
            is RefreshResult.Failed -> for (id in ids) {
                val base = known.value[id] ?: fromCatalog(id) ?: continue
                emit(base.copy(status = ControlMapper.status(result.reason)))
            }
        }
        live()?.collect { emitAll(ids, it) }
    }

    fun perform(id: String, action: ControlAction): Boolean {
        val model = known.value[id] ?: return false
        synchronized(inFlight) {
            inFlight.remove(id)?.cancel()
            inFlight[id] = scope.launch {
                val outcome = when (val result = entities.send(id, ControlMapper.command(model, action))) {
                    is SendResult.Confirmed -> ControlMapper.model(result.entity, result.state, emptyList()).copy(zone = model.zone)
                    SendResult.NotConfirmed -> model.copy(status = ControlStatus.NO_RESPONSE)
                    is SendResult.Failed -> model.copy(status = ControlMapper.status(result.reason))
                }
                remember(outcome)
                updates.emit(outcome)
            }
        }
        return true
    }

    private fun remember(model: ControlModel) = known.update { it + (model.id to model) }

    private suspend fun fromCatalog(id: String): ControlModel? {
        val result = entities.catalog() as? CatalogResult.Ok ?: return null
        val entity = result.entities.firstOrNull { it.uniqueId == id } ?: return null
        return ControlMapper.model(entity, null, result.areas)
    }

    private suspend fun SendChannel<ControlModel>.emit(model: ControlModel) {
        remember(model)
        send(model)
    }

    private suspend fun SendChannel<ControlModel>.emitAll(ids: Set<String>, result: RefreshResult.Ok) {
        for (id in ids) {
            val entity = result.entities[id]
            val previous = known.value[id]
            val model = when {
                entity != null && ControlMapper.eligible(entity) -> ControlMapper.model(entity, result.states[id], result.areas)
                result.complete ->
                    (previous ?: ControlModel(id, id, null, ControlType.LIGHT, false, false, null, ControlStatus.NOT_FOUND))
                        .copy(status = ControlStatus.NOT_FOUND)
                else -> previous?.copy(status = ControlStatus.UNAVAILABLE)
            } ?: continue
            emit(model)
        }
    }
}
