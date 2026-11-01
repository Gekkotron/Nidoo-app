package com.gekkotron.nidoo.feature.security

import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandFailure
import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.model.Device
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.PublishFailed
import com.gekkotron.nidoo.transport.TransportMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEntitySource : EntitySource {
    override val link = MutableStateFlow<LinkState>(LinkState.Connected(TransportMode.DEMO))
    override val entities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    override val devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    override val areas = MutableStateFlow<List<Area>>(emptyList())
    override val shortcuts: kotlinx.coroutines.flow.StateFlow<Map<String, com.gekkotron.nidoo.voice.VoiceShortcut>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
    override suspend fun saveShortcut(shortcut: com.gekkotron.nidoo.voice.VoiceShortcut) {}
    override suspend fun deleteShortcut(id: String) {}
    override val commandFailures = MutableSharedFlow<CommandFailure>()
    val stateMap = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val sent = mutableListOf<Pair<String, Command>>()
    var result: CommandResult = CommandResult.Accepted
    val overrides = mutableMapOf<String, UiOverride>()
    var failWrites = false

    override fun states() = stateMap
    override suspend fun send(uniqueId: String, command: Command): CommandResult {
        sent += uniqueId to command
        return result
    }
    override suspend fun setOverride(uniqueId: String, override: UiOverride) {
        if (failWrites) throw PublishFailed("offline")
        overrides[uniqueId] = override
    }
    override suspend fun setAreas(areas: List<Area>) = error("not used")
}
