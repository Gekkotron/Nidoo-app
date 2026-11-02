package com.gekkotron.nidoo.flows

/** The writes the app makes to the hub's flows: flip one on or off, or run one. */
interface FlowsClient {
    suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit>

    /** Asks the hub to run flow [id] now (`202` on success, `422` when disabled, invalid or busy). */
    suspend fun run(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("run"))
}
