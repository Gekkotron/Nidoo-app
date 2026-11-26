package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.feature.energy.di.energyModule
import com.gekkotron.nidoo.feature.energy.presentation.EnergyViewModel
import com.gekkotron.nidoo.feature.history.di.historyModule
import com.gekkotron.nidoo.feature.history.presentation.HistoryViewModel
import com.gekkotron.nidoo.feature.home.di.homeModule
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.feature.home.presentation.LabelText
import com.gekkotron.nidoo.feature.settings.di.settingsModule
import com.gekkotron.nidoo.feature.settings.presentation.PlatformSettings
import com.gekkotron.nidoo.feature.settings.presentation.SettingsViewModel
import com.gekkotron.nidoo.feature.settings.rooms.RoomOrderViewModel
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.shared.AppShellViewModel
import com.gekkotron.nidoo.shared.tabs.MainTabsViewModel
import com.gekkotron.nidoo.transport.MqttClientPort
import com.gekkotron.nidoo.transport.MqttPortListener
import com.gekkotron.nidoo.transport.TransportSpec
import org.koin.dsl.koinApplication
import kotlin.test.Test

class IosModuleTest {
    private object NoopPort : MqttClientPort {
        override fun connect(spec: TransportSpec.Direct, listener: MqttPortListener) = Unit
        override fun subscribe(filters: List<String>, qos: Int, done: (error: String?) -> Unit) = done(null)
        override fun unsubscribe(filters: List<String>, done: (error: String?) -> Unit) = done(null)
        override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean, done: (error: String?) -> Unit) = done(null)
        override fun disconnect() = Unit
    }

    @Test
    fun iosGraphResolves() {
        val app = koinApplication { modules(iosModule { NoopPort }, sharedModule, onboardingModule, homeModule, settingsModule, energyModule, historyModule, mainTabsModule) }
        try {
            val koin = app.koin
            koin.get<EntitySourceSession>()
            koin.get<HistoryClient>()
            koin.get<FlowsRepository>()
            koin.get<DashboardsRepository>()
            koin.get<ClimateRepository>()
            koin.get<LabelText>()
            koin.get<ConnectionTester>()
            koin.get<AppShellViewModel>()
            koin.get<SettingsViewModel>()
            koin.get<RoomOrderViewModel>()
            koin.get<PlatformSettings>()
            koin.get<EnergyViewModel>()
            koin.get<HistoryViewModel>()
            koin.get<MainTabsViewModel>()
        } finally {
            app.close()
        }
    }
}
