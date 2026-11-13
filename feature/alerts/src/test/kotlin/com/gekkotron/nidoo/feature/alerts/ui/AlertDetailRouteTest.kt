package com.gekkotron.nidoo.feature.alerts.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.alerts.Alert
import com.gekkotron.nidoo.alerts.AlertChannel
import com.gekkotron.nidoo.alerts.AlertRepository
import com.gekkotron.nidoo.alerts.Severity
import com.gekkotron.nidoo.alerts.db.AlertDatabase
import com.gekkotron.nidoo.alerts.media.Media
import com.gekkotron.nidoo.alerts.media.MediaSource
import com.gekkotron.nidoo.alerts.media.VideoCache
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.alerts.FakeEntitySource
import com.gekkotron.nidoo.feature.alerts.presentation.AlertDetailViewModel
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.push.ReplyQueue
import com.gekkotron.nidoo.push.ReplyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AlertDetailRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AlertDatabase
    private val source = FakeEntitySource()

    private object NoBindings : Bindings

    private val replies = object : ReplyQueue {
        override fun enqueue(alertId: String, actionId: String, label: String): UUID = UUID.randomUUID()
        override fun state(id: UUID): Flow<ReplyState> = emptyFlow()
        override fun cancelAll() = Unit
    }

    private val noClips = object : MediaSource {
        override suspend fun bytes(url: String, maxBytes: Long, onProgress: (Long, Long?) -> Unit) = Result.success(Media(ByteArray(0), "video/mp4"))
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlertDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `leaving from the device sheet closes the alert sheet too`() {
        // History opened from the device must not sit over a still-open alert sheet: during a back swipe it shows over History and swallows the gesture.
        val repo = AlertRepository(db.alerts())
        source.entities.value = mapOf("front_door" to Entity("front_door", null, "Front door", Kind.BINARY_SENSOR, "door", null, null, Capabilities(), NoBindings))
        runBlocking {
            repo.upsert(
                Alert(
                    "frigate-1", AlertChannel.CAMERA, Severity.WARNING, "Front door", "Person", null, null, "binary_sensor.front_door",
                    emptyList(), "frigate", false, false, Instant.EPOCH, Instant.EPOCH,
                ),
            )
        }
        val viewModel = AlertDetailViewModel(repo, VideoCache(File(tmp.root, "video"), noClips), replies, source)
        var dismissed = 0
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                AlertDetailRoute(
                    "frigate-1",
                    onDismiss = { dismissed++ },
                    deviceSheet = { _, _, onLeave -> TextButton(onClick = onLeave) { Text("Device history") } },
                    viewModel = viewModel,
                )
            }
        }
        compose.onNodeWithText("Open device").performScrollTo().performClick()
        compose.onNodeWithText("Device history").performClick()
        assertEquals(1, dismissed)
    }
}
