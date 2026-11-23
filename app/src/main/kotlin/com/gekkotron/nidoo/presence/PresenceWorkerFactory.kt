package com.gekkotron.nidoo.presence

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.push.TopicPublisher
import java.util.concurrent.TimeUnit

class PresenceWorkerFactory(
    private val publisher: TopicPublisher,
    private val preferences: AppPreferences,
    private val fixSource: CurrentLocationSource,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            PresenceWorker::class.java.name -> PresenceWorker(appContext, workerParameters, publisher, preferences, fixSource)
            PresenceRefreshWorker::class.java.name -> PresenceRefreshWorker(appContext, workerParameters, publisher, preferences, fixSource)
            else -> null
        }
}

/**
 * Enqueues one publish per transition. The opposite transition cancels unfinished work, so a stale state never lands last.
 * The same transition again leaves it alone: the geofence is registered again on every process start and reports the side
 * the phone is on each time, and replacing the work there would reset the attempts an unsettled EXIT is counting.
 */
object PresenceWork {
    fun enqueue(context: Context, state: PresenceState) {
        val request = OneTimeWorkRequestBuilder<PresenceWorker>()
            .addTag(PresenceWorker.TAG)
            .setInputData(workDataOf(PresenceWorker.KEY_STATE to state.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val workManager = WorkManager.getInstance(context)
        PresenceState.entries.filter { it != state }.forEach { workManager.cancelUniqueWork(uniqueName(it)) }
        workManager.enqueueUniqueWork(uniqueName(state), ExistingWorkPolicy.KEEP, request)
    }

    private fun uniqueName(state: PresenceState): String = "${PresenceWorker.TAG}-${state.name}"
}

/** Enqueues a refresh in response to a silent FCM `cmd=presence_refresh`. Network-constrained, expedited when possible. */
object PresenceRefreshWork {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PresenceRefreshWorker>()
            .addTag(PresenceRefreshWorker.TAG)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PresenceRefreshWorker.TAG, ExistingWorkPolicy.REPLACE, request)
    }
}
