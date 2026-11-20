package com.gekkotron.nidoo.flows

/** The writes the app makes to the hub's flows: flip one on or off, run one, or re-time a wake-up alarm. */
interface FlowsClient {
    suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit>

    /** Asks the hub to run flow [id] now (`202` on success, `422` when disabled, invalid or busy). */
    suspend fun run(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("run"))

    /** Re-times wake-up alarm [id] on the hub. [days] is 0 = Sunday .. 6 = Saturday. */
    suspend fun setWakeUp(id: String, hour: Int, minute: Int, days: List<Int>): Result<Unit> =
        Result.failure(UnsupportedOperationException("setWakeUp"))
}
