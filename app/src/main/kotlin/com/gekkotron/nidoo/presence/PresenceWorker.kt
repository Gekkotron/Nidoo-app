package com.gekkotron.nidoo.presence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.PresenceConfig
import com.gekkotron.nidoo.push.TopicPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Runs after a geofence transition.
 *
 * An ENTER is published as home at once: coming home must not wait for a fix, and a fix taken at the edge of the radius
 * can still read outside, after which no other transition would come to correct it.
 *
 * An EXIT is checked against a fresh fix, because Play services reports a false EXIT when an indoor position jumps.
 * A fix inside the radius publishes home and the work runs again later: either the EXIT was false and the fix stays
 * inside, or the fix was taken before the phone crossed the edge and a later one reads away.
 *
 * Publishes HA MQTT Discovery for the device_tracker and the new presence state. Retained so a late HA pick-up still sees
 * the right value. Three publishes: discovery config, availability online, state (home / not_home).
 */
class PresenceWorker(
    context: Context,
    params: WorkerParameters,
    private val publisher: TopicPublisher,
    private val preferences: AppPreferences,
    private val fixSource: CurrentLocationSource,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val stateName = inputData.getString(KEY_STATE) ?: return Result.failure()
        val hint = runCatching { PresenceState.valueOf(stateName) }.getOrNull() ?: return Result.failure()
        val config = preferences.presence.filterNotNull().first()
        if (!config.enabled || config.alias.isBlank()) return Result.success()
        val state = when (hint) {
            PresenceState.HOME -> PresenceState.HOME
            PresenceState.AWAY -> fixSource.current()?.let { PresenceDecision.stateOf(it, config) } ?: PresenceState.AWAY
        }
        val result = publishAll(config, state)
        val exitDenied = hint == PresenceState.AWAY && state == PresenceState.HOME
        return if (exitDenied && result is Result.Success && runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else result
    }

    private suspend fun publishAll(config: PresenceConfig, state: PresenceState): Result {
        val alias = config.alias
        val publishes = listOf(
            PresenceTopics.discovery(alias) to PresencePayloads.discovery(alias),
            PresenceTopics.availability(alias) to PresencePayloads.AVAILABLE_ONLINE,
            PresenceTopics.state(alias) to PresencePayloads.state(state),
        )
        for ((topic, payload) in publishes) {
            val error = try {
                publisher.publish(topic, payload.encodeToByteArray(), retain = true).exceptionOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            if (error != null) return retryOrFail()
        }
        return Result.success()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        const val TAG = "presence"
        const val KEY_STATE = "state"
        const val MAX_ATTEMPTS = 5
    }
}
