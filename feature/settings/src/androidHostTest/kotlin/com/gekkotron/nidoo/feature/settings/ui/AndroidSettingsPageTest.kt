package com.gekkotron.nidoo.feature.settings.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.create
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.settings.FakePushStatusSource
import com.gekkotron.nidoo.feature.settings.presentation.AndroidSettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.push.PushStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class AndroidSettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()

    private fun prefs() = AppPreferences.create(tmp.root.resolve("prefs.json"), scope)

    private fun viewModel(push: FakePushStatusSource = FakePushStatusSource(), prefs: AppPreferences = prefs()) =
        AndroidSettingsViewModel(push, prefs, SpeechAvailability.Status.Ready)

    private lateinit var registry: LifecycleRegistry

    private fun lifecycleOwner(initial: Lifecycle.State): LifecycleOwner {
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }
        registry = LifecycleRegistry.createUnsafe(owner)
        registry.currentState = initial
        return owner
    }

    private fun showPage(
        page: SettingsPage,
        vm: AndroidSettingsViewModel,
        owner: LifecycleOwner = lifecycleOwner(Lifecycle.State.RESUMED),
        clock: () -> Instant = Instant::now,
        presenceSection: @Composable (onInfo: (String) -> Unit) -> Unit = {},
    ) = compose.setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                val snackbar = remember { SnackbarHostState() }
                Column {
                    AndroidSettingsPage(page, snackbar, vm, clock, presenceSection)
                    SnackbarHost(snackbar)
                }
            }
        }
    }

    @Test
    fun `the push status is re-read every time the notifications page resumes`() {
        val push = FakePushStatusSource()
        showPage(SettingsPage.NOTIFICATIONS, viewModel(push), lifecycleOwner(Lifecycle.State.STARTED))
        compose.runOnIdle { assertEquals(0, push.refreshes) }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, push.refreshes) }
        compose.runOnIdle { registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(2, push.refreshes) }
        assertTrue(push.refreshes >= 2)
    }

    @Test
    fun `other pages leave the push status alone on resume`() {
        val push = FakePushStatusSource()
        showPage(SettingsPage.APPEARANCE, viewModel(push))
        compose.runOnIdle { registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(0, push.refreshes) }
        compose.onAllNodesWithText("Register again").assertCountIsZero()
    }

    @Test
    fun `the registered age moves on at resume without a new status emission`() {
        val at = Instant.parse("2026-10-07T10:00:00Z")
        var clock = at.plusSeconds(120)
        showPage(SettingsPage.NOTIFICATIONS, viewModel(FakePushStatusSource(PushStatus.Registered(at))), clock = { clock })
        compose.onNodeWithText("Registered · 2 min", substring = true).assertExists()
        clock = at.plusSeconds(420)
        compose.runOnIdle { registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Registered · 7 min", substring = true).assertExists()
    }

    @Test
    fun `the channels button opens the app's notification settings`() {
        showPage(SettingsPage.NOTIFICATIONS, viewModel(FakePushStatusSource(PushStatus.Demo)))
        compose.onNodeWithText("Notification channels").performClick()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started?.action)
        assertEquals(app.packageName, started?.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `the notifications page shows each status in words`() {
        val push = FakePushStatusSource(PushStatus.Registered(Instant.now().minusSeconds(120)))
        showPage(SettingsPage.NOTIFICATIONS, viewModel(push))
        compose.onNodeWithText("Registered · ", substring = true).assertExists()
        compose.onNodeWithText("2 min", substring = true).assertExists()
        push.status.value = PushStatus.Waiting
        compose.onNodeWithText("Waiting for connection").assertExists()
        push.status.value = PushStatus.Failed("broker did not answer in time")
        compose.onNodeWithText("Failed: broker did not answer in time").assertExists()
        push.status.value = PushStatus.NotificationsOff
        compose.onNodeWithText("Notifications off").assertExists()
        push.status.value = PushStatus.Demo
        compose.onNodeWithText("Not available in demo").assertExists()
    }

    @Test
    fun `Register again reaches the push source and the page stays`() {
        val push = FakePushStatusSource(PushStatus.Waiting)
        showPage(SettingsPage.NOTIFICATIONS, viewModel(push))
        compose.onNodeWithText("Register again").performClick()
        assertEquals(1, push.registerAgains)
        compose.onNodeWithText("Notification channels").assertExists()
    }

    @Test
    fun `in the demo Register again is disabled`() {
        showPage(SettingsPage.NOTIFICATIONS, viewModel(FakePushStatusSource(PushStatus.Demo)))
        compose.onNodeWithText("Register again").assertIsNotEnabled()
    }

    @Test
    fun `the presence page renders the presence section and its info reaches the snackbar`() {
        showPage(SettingsPage.PRESENCE, viewModel(), presenceSection = { info ->
            androidx.compose.material3.TextButton(onClick = { info("Home set") }) { androidx.compose.material3.Text("Set home") }
        })
        compose.onNodeWithText("Set home").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Home set").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the voice page shows the denied snackbar when the mic is refused`() {
        val prefs = prefs()
        // Answers every permission request with "denied", as a user tapping Don't allow would.
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, false)
            }
        }
        val activityOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val vm = viewModel(prefs = prefs)
        val lifecycle = lifecycleOwner(Lifecycle.State.RESUMED)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides activityOwner, LocalLifecycleOwner provides lifecycle) {
                NidooTheme(ThemeChoice.NIGHT_GLASS) {
                    val snackbar = remember { SnackbarHostState() }
                    Column {
                        AndroidSettingsPage(SettingsPage.VOICE, snackbar, vm)
                        SnackbarHost(snackbar)
                    }
                }
            }
        }
        compose.onNodeWithText("Voice commands").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Microphone permission is needed for voice commands.").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { prefs.voiceEnabled.value == false }
    }

    @Test
    fun `a page that is not Android's renders nothing`() {
        showPage(SettingsPage.ABOUT, viewModel())
        compose.onAllNodesWithText("Register again").assertCountIsZero()
        compose.onAllNodesWithText("Voice commands").assertCountIsZero()
    }

    private fun SemanticsNodeInteractionCollection.assertCountIsZero() = assertCountEquals(0)
}
