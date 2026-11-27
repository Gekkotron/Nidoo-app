package com.gekkotron.nidoo.shared

import androidx.compose.ui.test.assertIsEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
import com.gekkotron.nidoo.feature.energy.di.energyModule
import com.gekkotron.nidoo.feature.history.di.historyModule
import com.gekkotron.nidoo.shared.di.mainTabsModule
import com.gekkotron.nidoo.feature.home.di.homeModule
import com.gekkotron.nidoo.feature.home.presentation.HomeViewModel
import com.gekkotron.nidoo.feature.security.di.securityModule
import com.gekkotron.nidoo.feature.settings.di.settingsModule
import com.gekkotron.nidoo.feature.settings.presentation.NoPlatformSettings
import com.gekkotron.nidoo.feature.settings.presentation.PlatformSettings
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
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS shell, run on Robolectric: what the user sees across "Set up again". */
@RunWith(AndroidJUnit4::class)
class NidooAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-app-${Random.nextLong().toULong()}")
    /** The distinct sources handed out: Home's ViewModels each ask for one, so the same source is asked for many times. */
    private val sources: MutableSet<EntitySource> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    @After
    fun tearDown() {
        koinApp.close()
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
        single<PlatformSettings> { NoPlatformSettings }
    }

    /** Counts Home's ViewModels, built as `homeModule` builds them (Koin lets a later definition replace an earlier one). */
    private val homeCreations = AtomicInteger()
    private val countingHome = module {
        viewModel {
            homeCreations.incrementAndGet()
            HomeViewModel(
                source = get(),
                savedState = get(),
                defaultDispatcher = Dispatchers.Default,
                labels = get(),
                history = get(),
                preferences = get(),
                climate = get(),
                transport = get(),
            )
        }
    }

    private val koinApp by lazy { koinApplication { modules(platform, onboardingModule, sharedModule, homeModule, settingsModule, energyModule, historyModule, securityModule, mainTabsModule, countingHome) } }

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
        setUpAgainFromSettings()

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
        setUpAgainFromSettings()
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()

        assertEquals(2, sources.size)
        // The rule behind it: a new house gets a new HomeViewModel, one per house.
        assertEquals(2, homeCreations.get())
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

    /** Settings › Connection › "Set up connection again" › confirm. */
    private fun setUpAgainFromSettings() {
        openSettings()
        compose.onNodeWithText("Connection").performScrollTo().performClick()
        waitFor("Set up connection again")
        compose.onNodeWithText("Set up connection again").performScrollTo().performClick()
        waitFor("Set up again?")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")
    }

    private fun demoHome() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitForHome()
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Appearance")
    }

    private fun systemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun settingsOpensTheRealSettings() {
        demoHome()
        openSettings()
        compose.onNodeWithText("Connection").assertExists()
        compose.onNodeWithText("Notifications").assertDoesNotExist()
    }

    @Test
    fun setUpAgainFromConnectionReturnsToWelcome() {
        demoHome()
        setUpAgainFromSettings()
        compose.onNodeWithText("Scan setup code").assertIsEnabled()
    }

    @Test
    fun backFromASettingsPageReturnsToTheList() {
        demoHome()
        openSettings()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        waitFor("Theme")
        systemBack()
        waitFor("Appearance")
        compose.onNodeWithText("Theme").assertDoesNotExist()
        systemBack()
        waitForHome()
    }

    @Test
    fun backOnHomeStaysOnHome() {
        demoHome()
        systemBack()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun roomOrderOpensFromSettings() {
        demoHome()
        openSettings()
        compose.onNodeWithText("Rooms").performClick()
        waitFor("Drag to change the order of the room chips on Home.")
    }

    @Test
    fun homeKeepsItsViewModelWhenTheProfileIsEdited() {
        showApp()
        waitFor("Try the demo")
        val profiles = koinApp.koin.get<ProfileRepository>()
        runBlocking {
            profiles.save(ConnectionProfile.Direct(LocalBroker("broker.local")))
            profiles.finishRooms()
        }
        waitForHome()
        assertEquals(1, homeCreations.get())
        openSettings()
        runBlocking { profiles.setHistoryUrl("http://hub.lan:8090") }
        compose.waitForIdle()
        systemBack()
        waitForHome()
        assertEquals(1, homeCreations.get())
    }

    @Test
    fun homeShowsTheTabBarAndOpensEnergy() {
        demoHome()
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Energy").assertExists()
        compose.onNodeWithText("Security").assertExists()
        compose.onNodeWithText("Energy").performClick()
        // Energy's own content (its empty state's button, or the live subtitle) shows, and Home's gear is gone.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Go to Home").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Live", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Home").performClick()
        waitForHome()
    }

    @Test
    fun securityTabOpensSecurity() {
        demoHome()
        compose.onNodeWithText("Security").performClick()
        // The tab label and the screen title both read "Security"; the Security screen has its own content and Home's gear is gone.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Security").fetchSemanticsNodes().size >= 2 }
        assertTrue(compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isEmpty())
        // Security's room chips include a "Home" chip, so pick the tab by its role.
        compose.onNode(hasText("Home") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        waitForHome()
    }

    @Test
    fun settingsHasNoTabBar() {
        demoHome()
        openSettings()
        compose.onNodeWithText("Energy").assertDoesNotExist()
    }
}
