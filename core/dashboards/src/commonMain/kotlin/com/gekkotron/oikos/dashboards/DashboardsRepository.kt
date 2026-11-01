package com.gekkotron.oikos.dashboards

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [dashboards] is sorted by order, then name (case-insensitive), then id. [isSettled] flips once
 * the retained burst is over, so the UI can tell "no dashboards" from "not loaded yet".
 */
data class DashboardsSnapshot(
    val isSettled: Boolean,
    val dashboards: List<Dashboard>,
    /**
     * Version of the repository state this was built from. It takes part in equality on purpose:
     * the repository refuses stale publishes by version, and two snapshots with equal content but
     * different versions must not collapse into one stored value.
     */
    val version: Long = 0L,
)

/**
 * The hub's dashboards (retained MQTT, `oikos/ui/dashboards/<id>`). Read-only in the app.
 *
 * All state lives in one immutable [State] behind a [MutableStateFlow], so [onRetained]
 * (MQTT thread) and the timers update it atomically.
 */
class DashboardsRepository(
    private val scope: CoroutineScope,
    private val settleAfter: Duration = 2.seconds,
    private val log: (String) -> Unit = {},
    /** How long after a link-up a known dashboard has to be replayed by the broker before it is dropped. */
    private val confirmAfter: Duration = 5.seconds,
) : DashboardsSink {

    private class State(
        val server: Map<String, Dashboard> = emptyMap(),
        val settled: Boolean = false,
        /**
         * Rows known before the last link-up that the broker has not replayed since. A dashboard
         * deleted while the link was down never gets its empty retained publish, so these are
         * dropped once the replay window ([confirmAfter]) is over.
         */
        val unconfirmed: Set<String> = emptySet(),
        /** Bumped by every transform so a late publish can't overwrite a newer snapshot. */
        val version: Long = 0L,
    ) {
        fun copy(
            server: Map<String, Dashboard> = this.server,
            settled: Boolean = this.settled,
            unconfirmed: Set<String> = this.unconfirmed,
        ) = State(server, settled, unconfirmed, version)

        fun withVersion(v: Long) = State(server, settled, unconfirmed, v)

        fun toSnapshot() = DashboardsSnapshot(
            isSettled = settled,
            dashboards = server.values.sortedWith(
                compareBy<Dashboard> { it.order }.thenBy { it.name.lowercase() }.thenBy { it.id },
            ),
            version = version,
        )
    }

    private val state = MutableStateFlow(State())
    private val settleTimer = MutableStateFlow<Job?>(null)
    private val confirmTimer = MutableStateFlow<Job?>(null)

    private val _dashboards = MutableStateFlow(state.value.toSnapshot())
    val dashboards: StateFlow<DashboardsSnapshot> = _dashboards.asStateFlow()

    private fun update(transform: (State) -> State) {
        publish(state.updateAndGet { s -> transform(s).withVersion(s.version + 1) })
    }

    /** Publishes [s]'s snapshot unless a newer one is already out, so racing writers can't regress it. */
    private fun publish(s: State) {
        val snap = s.toSnapshot()
        _dashboards.update { cur -> if (snap.version > cur.version) snap else cur }
    }

    override fun onRetained(id: String, payload: String?) {
        val dashboard: Dashboard? = if (payload.isNullOrEmpty()) {
            null
        } else {
            try {
                DashboardJson.decode(id, payload)
            } catch (e: Exception) {
                log("dropping malformed payload for '$id': ${e.message}")
                return
            }
        }
        update { s ->
            s.copy(
                server = if (dashboard == null) s.server - id else s.server + (id to dashboard),
                settled = true, // any retained message ends the burst
                unconfirmed = s.unconfirmed - id,
            )
        }
        cancelTimer()
    }

    override fun markLinkUp() {
        // Every row now has to be replayed by the broker to stay; see [State.unconfirmed].
        update { s -> s.copy(unconfirmed = s.server.keys) }
        val confirm = scope.launch(start = CoroutineStart.LAZY) {
            delay(confirmAfter)
            update { s -> s.copy(server = s.server - s.unconfirmed, unconfirmed = emptySet()) }
        }
        confirmTimer.getAndUpdate { confirm }?.cancel()
        confirm.start()

        if (state.value.settled) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(settleAfter)
            update { it.copy(settled = true) }
        }
        settleTimer.getAndUpdate { job }?.cancel()
        job.start()
    }

    override fun clear() {
        cancelTimer()
        confirmTimer.getAndUpdate { null }?.cancel()
        update { s -> s.copy(server = emptyMap(), settled = false, unconfirmed = emptySet()) }
    }

    private fun cancelTimer() {
        settleTimer.getAndUpdate { null }?.cancel()
    }
}
