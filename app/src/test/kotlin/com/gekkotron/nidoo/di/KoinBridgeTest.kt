package com.gekkotron.nidoo.di

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.create
import com.gekkotron.nidoo.feature.automations.presentation.AutomationsViewModel
import com.gekkotron.nidoo.feature.energy.presentation.EnergyViewModel
import com.gekkotron.nidoo.feature.history.presentation.HistoryViewModel
import com.gekkotron.nidoo.feature.security.presentation.SecurityViewModel
import com.gekkotron.nidoo.feature.home.presentation.CustomizeViewModel
import com.gekkotron.nidoo.feature.home.presentation.DashboardViewModel
import com.gekkotron.nidoo.feature.home.presentation.HomeViewModel
import com.gekkotron.nidoo.feature.home.rooms.RoomsViewModel
import com.gekkotron.nidoo.feature.onboarding.OnboardingViewModel
import com.gekkotron.nidoo.feature.settings.presentation.AndroidSettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.PresenceSettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.SettingsViewModel
import com.gekkotron.nidoo.feature.settings.rooms.RoomOrderViewModel
import com.gekkotron.nidoo.flows.FlowsClient
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.history.EnergyHistory
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.push.PushStatusSource
import com.gekkotron.nidoo.shared.tabs.MainTabsViewModel
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.MqttTransport
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class KoinBridgeTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob())
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        stopKoin()
        scope.cancel()
    }

    private inline fun <reified T> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("unused") } as T

    /** A Hilt stand-in: fixed profiles and preferences; EntitySource is unscoped in Hilt, so a new one per call. */
    private inner class FakeBridge(private val liveSource: Boolean = false) : KoinBridgeEntryPoint {
        val profiles = ProfileRepository.create(folder.newFile(), stub<SecretStore>(), scope)
        val preferences = AppPreferences.create(folder.newFile(), scope)
        val transport = FakeMqttTransport()
        private val session = EntitySourceSession(transport, scope)
        var sourcesHandedOut = 0
        override fun profiles() = profiles
        override fun preferences() = preferences
        // [liveSource]: Home's ViewModels read the source as they are built, so they need a real (offline) one.
        override fun entitySource(): EntitySource =
            (if (liveSource) session.current(null) else stub<EntitySource>()).also { sourcesHandedOut++ }
        val tester = ConnectionTester { emptyFlow() }
        override fun connectionTester() = tester
        val history = HistoryClient.None
        val dashboards = DashboardsRepository(scope)
        val flows = FlowsRepository(object : FlowsClient {
            override suspend fun setEnabled(id: String, enabled: Boolean) = Result.success(Unit)
        }, scope)
        val climate = ClimateRepository(scope)
        override fun historyClient() = history
        override fun dashboards() = dashboards
        override fun flows() = flows
        override fun climate() = climate
        override fun transport(): MqttTransport = transport
        val push = object : PushStatusSource {
            override val status = MutableStateFlow<PushStatus>(PushStatus.Waiting)
            override fun refresh() = Unit
            override fun registerAgain() = Unit
        }
        override fun pushStatus(): PushStatusSource = push
    }

    @Test
    fun bridgeHandsOutHiltsInstances() {
        val hilt = FakeBridge()
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        assertSame(hilt.profiles, koin.get<ProfileRepository>())
        assertSame(hilt.preferences, koin.get<AppPreferences>())
        assertSame(hilt.tester, koin.get<ConnectionTester>())
    }

    /** Onboarding's ViewModel comes from Koin now (iOS spec §9), built from Hilt's repository and tester. */
    @Test
    fun onboardingViewModelResolves() {
        startNidooKoin(app, FakeBridge())
        GlobalContext.get().get<OnboardingViewModel>()
    }

    /** Home's ViewModels come from Koin too (iOS spec §9), built from what Hilt hands over. */
    @Test
    fun homeViewModelsResolve() {
        val hilt = FakeBridge(liveSource = true)
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        koin.get<HomeViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<RoomsViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<DashboardViewModel>()
        koin.get<CustomizeViewModel>()
        assertSame(hilt.dashboards, koin.get<DashboardsRepository>())
        assertSame(hilt.transport, koin.get<MqttTransport>())
    }

    /** Settings' ViewModels come from Koin too: the common ones, and the Android-only pages' two. */
    @Test
    fun settingsViewModelsResolve() {
        startNidooKoin(app, FakeBridge(liveSource = true))
        val koin = GlobalContext.get()
        koin.get<SettingsViewModel>()
        koin.get<RoomOrderViewModel>()
        koin.get<AndroidSettingsViewModel>()
        koin.get<PresenceSettingsViewModel>()
    }

    @Test
    fun entitySourceIsAskedOfHiltEachTime() {
        val hilt = FakeBridge()
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        assertNotSame(koin.get<EntitySource>(), koin.get<EntitySource>())
        assertEquals(2, hilt.sourcesHandedOut)
    }

    /** Energy, History and the tab bar come from Koin too. */
    @Test
    fun energyHistoryAndTabViewModelsResolve() {
        startNidooKoin(app, FakeBridge(liveSource = true))
        val koin = GlobalContext.get()
        koin.get<EnergyViewModel>()
        koin.get<HistoryViewModel>()
        koin.get<MainTabsViewModel>()
        koin.get<SecurityViewModel>()
        koin.get<AutomationsViewModel>()
    }

    /** EnergyHistory reads an unscoped EntitySource, so each one asks Hilt again instead of pinning the first. */
    @Test
    fun energyHistoryIsAskedForItsSourceEachTime() {
        val hilt = FakeBridge()
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        assertNotSame(koin.get<EnergyHistory>(), koin.get<EnergyHistory>())
        assertEquals(2, hilt.sourcesHandedOut)
    }

    @Test
    fun startingTwiceReplacesTheGraph() {
        val first = FakeBridge()
        val second = FakeBridge()
        startNidooKoin(app, first)
        startNidooKoin(app, second)
        assertSame(second.preferences, GlobalContext.get().get<AppPreferences>())
    }
}
