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
 * Runs after a geofence transition. The transition is only a hint: Play services reports a false EXIT when an indoor
 * position jumps, and the work can wait hours for a network (an EXIT seen offline runs once the phone is back home).
 * So the state published is where the phone is when the work runs, from a fresh fix.
 *
 * When there is no fix, or the fix cannot tell, an EXIT is not trusted yet: the work retries (backoff 30 s, 60 s,
 * 120 s, 240 s) and publishes AWAY on the last attempt only, about 7 minutes later, if no fix has settled it by then.
 * A wrong AWAY arms whatever the hub ties to presence, and an indoor phone often has no clear fix for a while. An ENTER
 * with no clear fix is published at once, since a late HOME is the costlier mistake there.
 *
 * A clear fix that contradicts the transition is not final either. Play services fires at the edge of the radius, so the
 * fix taken right then often still reads the side the phone is leaving, and no other transition follows to correct it.
 * That state is published and the work retries on the same backoff, until a fix agrees with the transition or the
 * attempts run out (a false EXIT then stays on HOME).
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
        val located = fixSource.current()?.let { PresenceDecision.stateOf(it, config) }
        val state = when {
            located != null -> located
            hint == PresenceState.AWAY && !isLastAttempt() -> return Result.retry()
            else -> hint
        }
        val published = publishAll(config, state)
        // The fix contradicts the transition: it is published, since that is where the phone is, and checked again.
        return if (published == Result.success() && state != hint && !isLastAttempt()) Result.retry() else published
    }

    private fun isLastAttempt(): Boolean = runAttemptCount + 1 >= MAX_ATTEMPTS

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
        if (isLastAttempt()) Result.failure() else Result.retry()

    companion object {
        const val TAG = "presence"
        const val KEY_STATE = "state"
        const val MAX_ATTEMPTS = 5
    }
}
