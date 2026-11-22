package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The transport the saved profile and route ask for; null without a profile (spec §2). Feeds `ActiveTransport`. */
fun profileSpecs(profiles: ProfileRepository): Flow<TransportSpec?> = flow {
    // installId() is a suspend DataStore write; resolved once rather than re-run on every emission.
    val id = profiles.installId()
    emitAll(combine(profiles.state, profiles.route) { state, route -> state.toSpec(clientIdFor(id), route) })
}
