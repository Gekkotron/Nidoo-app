package com.gekkotron.nidoo.shared

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.demo.DemoBroker
import com.gekkotron.nidoo.feature.home.di.homeModule
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.flows.FlowsClient
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.shared.di.sharedModule
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.MqttTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.assertEquals

/** The iOS shell, run on Robolectric: what the user sees across "Set up again". */
@RunWith(AndroidJUnit4::class)
class NidooAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-app-${Random.nextLong().toULong()}")
    /** The distinct sources handed out: Home's ViewModels each ask for one, so the same source is asked for many times. */
    private val sources: MutableSet<EntitySource> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    @After
    fun tearDown() {
        scope.cancel()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private object PlainSecrets : SecretStore {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(cipher: ByteArray) = cipher
    }

    private object NoFlowsClient : FlowsClient {
        override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    }

    /** What the iOS module provides: the demo house on the transport, fakes for the HTTP clients. */
    private val platform = module {
        single { ProfileRepository.create(dir.resolve(ProfileRepository.FILE), PlainSecrets, scope) }
        single { AppPreferences.create(dir.resolve(AppPreferences.FILE), scope) }
        single<MqttTransport> { DemoBroker(scope) }
        single { EntitySourceSession(get(), scope) }
        single<HistoryClient> { HistoryClient.None }
        single { DashboardsRepository(scope) }
        single { FlowsRepository(NoFlowsClient, scope) }
        single { ClimateRepository(scope) }
        factory<EntitySource> {
            get<EntitySourceSession>().current((get<ProfileRepository>().state.value as? ProfileState.Ready)?.profile).also { sources += it }
        }
        single { ConnectionTester { emptyFlow() } }
    }

    private val koinApp by lazy { koinApplication { modules(platform, onboardingModule, sharedModule, homeModule) } }

    private fun showApp() {
        compose.setContent { KoinIsolatedContext(koinApp) { NidooApp() } }
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(label: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }

    /** Home's own controls: the settings button is on screen only there (the Rooms step and Settings have none). */
    private fun waitForHome() = waitForDescription("Settings")

    @Test
    fun setUpAgainAfterTheDemoShowsAWorkingWelcome() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Set up again")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")

        compose.onNodeWithText("Scan setup code").assertIsEnabled()
        compose.onNodeWithText("Enter manually").assertIsEnabled()
        compose.onNodeWithText("Try the demo").assertIsEnabled()
    }

    @Test
    fun aNewHouseGetsANewSource() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Set up again")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()

        assertEquals(2, sources.size)
    }

    @Test
    fun aNewProfileShowsTheRoomsStepThenHome() {
        showApp()
        waitFor("Try the demo")
        // A saved direct profile starts with the Rooms step; the demo broker stands in for its hub.
        val profiles = koinApp.koin.get<ProfileRepository>()
        runBlocking { profiles.save(ConnectionProfile.Direct(LocalBroker("broker.local"))) }
        waitFor("Which room is each device in?")
        // The button ends a long list, which a LazyColumn composes only once it is scrolled to.
        val finish = hasText("Skip") or hasText("Done")
        compose.waitUntil(5_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(finish)
        compose.onNode(finish).performClick()
        waitForHome()
        // A demo device card, which only Home draws.
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Floor lamp", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun settingsOffersSetUpAgain() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Set up again")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")
        compose.onNodeWithText("Scan setup code").assertIsEnabled()
    }
}
