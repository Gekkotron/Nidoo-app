package com.gekkotron.nidoo.feature.widget.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.gekkotron.nidoo.feature.widget.CatalogResult
import com.gekkotron.nidoo.feature.widget.Failure
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.SendResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.feature.widget.WidgetRefresher
import com.gekkotron.nidoo.feature.widget.model.TileIcon
import com.gekkotron.nidoo.feature.widget.model.TileSnapshot
import com.gekkotron.nidoo.feature.widget.model.TileStatus
import com.gekkotron.nidoo.feature.widget.model.TileValue
import com.gekkotron.nidoo.feature.widget.model.WidgetCommand
import com.gekkotron.nidoo.feature.widget.model.WidgetSnapshot
import com.gekkotron.nidoo.feature.widget.testing.MemConfigs
import com.gekkotron.nidoo.feature.widget.testing.MemSnapshots
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Kind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetCommandWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sent = mutableListOf<Command>()
    private val entities = object : WidgetEntities {
        override suspend fun refresh(ids: Set<String>) = RefreshResult.Failed(Failure.OFFLINE)
        override suspend fun send(id: String, command: Command): SendResult {
            sent += command
            return SendResult.NotConfirmed
        }
        override suspend fun catalog() = CatalogResult.Failed(Failure.OFFLINE)
    }
    private val snapshots = MemSnapshots().apply {
        flow.value = WidgetSnapshot(tiles = mapOf("a" to TileSnapshot("a", "Alarm", Kind.ALARM_PANEL, TileIcon.ALARM, TileValue.None)))
    }
    private val refresher = WidgetRefresher(entities, MemConfigs(), snapshots, {})

    private fun worker(input: androidx.work.Data) = TestListenableWorkerBuilder<WidgetCommandWorker>(context)
        .setInputData(input)
        .setWorkerFactory(WidgetWorkerFactory { refresher })
        .build()

    @Test
    fun the_work_input_never_holds_the_code() {
        val input = WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Disarm, "1234")
        assertFalse(input.keyValueMap.values.any { it.toString().contains("1234") })
    }

    @Test
    fun the_code_reaches_the_command_once() = runBlocking {
        val input = WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Disarm, "1234")
        assertEquals(ListenableWorker.Result.success(), worker(input).doWork())
        assertEquals(listOf<Command>(Command.Disarm("1234")), sent)
        // The vault gave the code away: a retry of the same input finds nothing and never sends without it.
        worker(input).doWork()
        assertEquals(1, sent.size)
        assertEquals(TileStatus.FAILED, snapshots.flow.value.tiles["a"]?.status)
    }

    @Test
    fun a_lost_code_marks_the_tile_failed_without_sending() = runBlocking {
        val input = WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Disarm, "1234")
        CodeVault.take(input.getString(WidgetCommandWorker.KEY_CODE_TOKEN)!!) // the process died: the vault is empty
        worker(input).doWork()
        assertTrue(sent.isEmpty())
        assertEquals(TileStatus.FAILED, snapshots.flow.value.tiles["a"]?.status)
    }

    @Test
    fun a_command_held_back_too_long_is_dropped_and_marked_failed() = runBlocking {
        val input = WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Disarm, null, enqueuedAt = 1L)
        assertEquals(ListenableWorker.Result.success(), worker(input).doWork())
        assertTrue(sent.isEmpty())
        assertEquals(TileStatus.FAILED, snapshots.flow.value.tiles["a"]?.status)
    }

    @Test
    fun a_fresh_command_is_sent() = runBlocking {
        val input = WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Disarm, null, enqueuedAt = System.currentTimeMillis())
        worker(input).doWork()
        assertEquals(listOf<Command>(Command.Disarm(null)), sent)
    }

    @Test
    fun a_command_without_a_code_has_no_token() {
        assertNull(WorkManagerWidgetScheduler.commandInput("a", WidgetCommand.Toggle, null).getString(WidgetCommandWorker.KEY_CODE_TOKEN))
    }
}
