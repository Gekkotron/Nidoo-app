package com.gekkotron.nidoo.dashboards

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
 * the source reports its retained replay as finished ([markReplayed]), or [fallbackSettleAfter] after link-up when it never does,
 * so the UI can tell "no dashboards" from "not loaded yet".
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
    /**
     * Dashboards the reconnect confirm window removed because the broker did not replay them. That
     * is a timing guess, not a deletion: the id leaves this set if the dashboard is replayed later.
     * An explicit empty retained payload (a real deletion) is never listed here.
     */
    val dropped: Set<String> = emptySet(),
)

/**
 * The hub's dashboards (retained MQTT, `nidoo/ui/dashboards/<id>`). Read-only in the app.
 *
 * All state lives in one immutable [State] behind a [MutableStateFlow], so [onRetained]
 * (MQTT thread) and the timers update it atomically.
 */
class DashboardsRepository(
    private val scope: CoroutineScope,
    /** Settles this long after a link-up even if the source never calls [markReplayed] (a broker with no messages). */
    private val fallbackSettleAfter: Duration = 10.seconds,
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
        val dropped: Set<String> = emptySet(),
        /** Bumped by every transform so a late publish can't overwrite a newer snapshot. */
        val version: Long = 0L,
    ) {
        fun copy(
            server: Map<String, Dashboard> = this.server,
            settled: Boolean = this.settled,
            unconfirmed: Set<String> = this.unconfirmed,
            dropped: Set<String> = this.dropped,
        ) = State(server, settled, unconfirmed, dropped, version)

        fun withVersion(v: Long) = State(server, settled, unconfirmed, dropped, v)

        fun toSnapshot() = DashboardsSnapshot(
            isSettled = settled,
            dashboards = server.values.sortedWith(
                compareBy<Dashboard> { it.order }.thenBy { it.name.lowercase() }.thenBy { it.id },
            ),
            version = version,
            dropped = dropped,
        )
    }

    private val state = MutableStateFlow(State())
    private val fallbackTimer = MutableStateFlow<Job?>(null)
    private val confirmTimer = MutableStateFlow<Job?>(null)

    private val _dashboards = MutableStateFlow(state.value.toSnapshot())
    val dashboards: StateFlow<DashboardsSnapshot> = _dashboards.asStateFlow()

    /**
     * Applies [transform]; the version only moves (and a snapshot is only published) when something
     * a snapshot shows changed. Bookkeeping such as [State.unconfirmed] is stored silently.
     */
    private fun update(transform: (State) -> State) {
        publish(
            state.updateAndGet { s ->
                val n = transform(s)
                val visible = n.server != s.server || n.settled != s.settled || n.dropped != s.dropped
                n.withVersion(if (visible) s.version + 1 else s.version)
            },
        )
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
                // The broker did replay the id: keep the old value and don't let the confirm window drop it.
                update { s -> s.copy(unconfirmed = s.unconfirmed - id) }
                return
            }
        }
        update { s ->
            if (dashboard == null) {
                s.copy(server = s.server - id, unconfirmed = s.unconfirmed - id, dropped = s.dropped - id)
            } else {
                s.copy(
                    server = if (s.server[id] == dashboard) s.server else s.server + (id to dashboard),
                    unconfirmed = s.unconfirmed - id,
                    dropped = s.dropped - id,
                )
            }
        }
    }

    override fun markReplayed() {
        fallbackTimer.getAndUpdate { null }?.cancel()
        update { it.copy(settled = true) }
    }

    override fun markLinkUp() {
        // Every row now has to be replayed by the broker to stay; see [State.unconfirmed].
        update { s -> s.copy(unconfirmed = s.server.keys) }
        val confirm = scope.launch(start = CoroutineStart.LAZY) {
            delay(confirmAfter)
            update { s ->
                s.copy(server = s.server - s.unconfirmed, unconfirmed = emptySet(), dropped = s.dropped + s.unconfirmed)
            }
        }
        confirmTimer.getAndUpdate { confirm }?.cancel()
        confirm.start()

        if (!state.value.settled) {
            val fallback = scope.launch(start = CoroutineStart.LAZY) {
                delay(fallbackSettleAfter)
                update { it.copy(settled = true) }
            }
            fallbackTimer.getAndUpdate { fallback }?.cancel()
            fallback.start()
        }
    }

    override fun clear() {
        fallbackTimer.getAndUpdate { null }?.cancel()
        confirmTimer.getAndUpdate { null }?.cancel()
        update { s -> s.copy(server = emptyMap(), settled = false, unconfirmed = emptySet(), dropped = emptySet()) }
    }
}
