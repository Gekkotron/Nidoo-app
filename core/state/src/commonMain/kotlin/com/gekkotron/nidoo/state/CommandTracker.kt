package com.gekkotron.nidoo.state

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandFailure
import com.gekkotron.nidoo.model.StateValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Optimistic commands (spec §4.4): pending → confirmed by any state message, or reverted after [timeout]. */
class CommandTracker(
    private val store: StateStore,
    private val scope: CoroutineScope,
    private val timeout: Duration = 5.seconds,
) {
    private val _failures = MutableSharedFlow<CommandFailure>(extraBufferCapacity = 64)
    val failures: SharedFlow<CommandFailure> = _failures.asSharedFlow()

    private val lock = SynchronizedObject()
    private val timers = mutableMapOf<String, Job>()

    private fun removeTimer(uniqueId: String): Job? = synchronized(lock) { timers.remove(uniqueId) }

    /** Removes the timer only if it is still [job] (a newer command may have replaced it). */
    private fun removeTimer(uniqueId: String, job: Job): Unit = synchronized(lock) {
        if (timers[uniqueId] === job) timers.remove(uniqueId)
    }

    /**
     * Call before publishing. Without feedback, the caller confirms with [StateStore.confirmPending].
     * [confirmBy] restricts which state topics confirm (null = any state topic of the entity).
     */
    fun track(uniqueId: String, command: Command, predicted: StateValue?, hasFeedback: Boolean, confirmBy: Set<String>? = null) {
        removeTimer(uniqueId)?.cancel()
        if (predicted == null) return
        store.markPending(uniqueId, predicted, confirmBy)
        if (!hasFeedback) return
        val timer = scope.launch {
            delay(timeout)
            removeTimer(uniqueId, coroutineContext.job)
            if (store.revert(uniqueId)) _failures.emit(CommandFailure(uniqueId, command, "Not confirmed"))
        }
        synchronized(lock) { timers[uniqueId] = timer }
    }

    /** The publish failed: drop the prediction without reporting a timeout. */
    fun abort(uniqueId: String) {
        removeTimer(uniqueId)?.cancel()
        store.revert(uniqueId)
    }
}
