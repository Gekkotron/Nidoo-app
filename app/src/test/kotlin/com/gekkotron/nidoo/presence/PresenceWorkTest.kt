package com.gekkotron.nidoo.presence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PresenceWorkTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    /** Unfinished presence work; it waits for a network the test driver never reports. */
    private fun pending(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag(PresenceWorker.TAG).get().filter { !it.state.isFinished }

    @Test
    fun `the same transition again keeps the unfinished work and its attempt count`() {
        // The geofence is registered again on every process start and reports the side the phone is on each time.
        PresenceWork.enqueue(context, PresenceState.AWAY)
        val first = pending().single().id
        PresenceWork.enqueue(context, PresenceState.AWAY)
        assertEquals(first, pending().single().id)
    }

    @Test
    fun `the opposite transition replaces the unfinished work`() {
        PresenceWork.enqueue(context, PresenceState.AWAY)
        val away = pending().single().id
        PresenceWork.enqueue(context, PresenceState.HOME)
        val left = pending()
        assertEquals(1, left.size, "only the newest transition may stay queued")
        assertTrue(left.single().id != away)
    }
}
