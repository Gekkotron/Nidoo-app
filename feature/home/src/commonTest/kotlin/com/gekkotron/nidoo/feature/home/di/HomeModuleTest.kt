package com.gekkotron.nidoo.feature.home.di

import androidx.lifecycle.SavedStateHandle
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.feature.home.presentation.CustomizeViewModel
import com.gekkotron.nidoo.feature.home.presentation.DashboardViewModel
import com.gekkotron.nidoo.feature.home.presentation.HomeViewModel
import com.gekkotron.nidoo.feature.home.rooms.RoomsViewModel
import com.gekkotron.nidoo.flows.FlowsClient
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.MqttTransport
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeModuleTest {
    private val scope = CoroutineScope(Job())
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-home-${Random.nextLong().toULong()}")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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

    /** What each platform module provides (the iOS module, Hilt's bridge on Android). */
    private val platform = module {
        single { ProfileRepository.create(dir.resolve(ProfileRepository.FILE), PlainSecrets, scope) }
        single { AppPreferences.create(dir.resolve(AppPreferences.FILE), scope) }
        single<MqttTransport> { FakeMqttTransport() }
        single { EntitySourceSession(get(), scope) }
        factory<EntitySource> { get<EntitySourceSession>().current(null) }
        single<HistoryClient> { HistoryClient.None }
        single { DashboardsRepository(scope) }
        single { FlowsRepository(NoFlowsClient, scope) }
        single { ClimateRepository(scope) }
    }

    @Test
    fun homeModuleResolvesItsViewModels() {
        val koin = koinApplication { modules(platform, homeModule) }.koin
        koin.get<HomeViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<RoomsViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<DashboardViewModel>()
        koin.get<CustomizeViewModel>()
    }
}
