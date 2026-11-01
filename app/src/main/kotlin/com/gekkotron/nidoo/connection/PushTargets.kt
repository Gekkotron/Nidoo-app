package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.alerts.PushTopics
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.push.PushTarget
import com.gekkotron.nidoo.push.PushTargets
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun pushTargetFor(state: ProfileState, installId: String): PushTarget = when (state) {
    is ProfileState.Ready -> when (val profile = state.profile) {
        ConnectionProfile.Demo -> PushTarget.Demo
        is ConnectionProfile.Direct -> PushTarget.Ready(installId, PushTopics.tokenTopic(profile.pushTokenTopic, installId))
    }
    ProfileState.Loading, ProfileState.None, ProfileState.Locked -> PushTarget.None
}

class ProfilePushTargets(private val profiles: ProfileRepository) : PushTargets {
    override suspend fun current(): PushTarget {
        val state = profiles.state.first { it !is ProfileState.Loading }
        return pushTargetFor(state, profiles.installId())
    }

    override val changes: Flow<PushTarget> = flow {
        val id = profiles.installId()
        emitAll(profiles.state.filter { it !is ProfileState.Loading }.map { pushTargetFor(it, id) }.distinctUntilChanged())
    }
}

/**
 * The spec a background publish uses: the live one, but with its own MQTT client id. A second client with the live
 * id would make the broker disconnect the first.
 */
suspend fun ProfileRepository.oneShotSpec(): TransportSpec? {
    val ready = state.first { it !is ProfileState.Loading }
    return ready.toSpec(clientIdFor(installId()) + "-push", route.value)
}
