package com.gekkotron.nidoo.di

import android.app.Application
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.feature.home.di.homeModule
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.feature.settings.di.androidSettingsModule
import com.gekkotron.nidoo.feature.settings.di.settingsModule
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.push.PushStatusSource
import com.gekkotron.nidoo.shared.di.mainTabsModule
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.MqttTransport
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What Hilt hands over to Koin while the two coexist (iOS spec §9). The common ViewModels that move to Koin in
 * Phase 4 get their dependencies from here on Android and from the iOS Koin module on iOS.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface KoinBridgeEntryPoint {
    fun profiles(): ProfileRepository
    fun preferences(): AppPreferences
    fun entitySource(): EntitySource
    fun connectionTester(): ConnectionTester
    fun historyClient(): HistoryClient
    fun dashboards(): DashboardsRepository
    fun flows(): FlowsRepository
    fun climate(): ClimateRepository
    fun transport(): MqttTransport
    fun pushStatus(): PushStatusSource
}

/** Each type is a factory that asks Hilt, so Hilt keeps owning the lifetimes (EntitySource is unscoped there). */
fun hiltBridgeModule(bridge: KoinBridgeEntryPoint): Module = module {
    factory { bridge.profiles() }
    factory { bridge.preferences() }
    factory { bridge.entitySource() }
    factory { bridge.connectionTester() }
    factory { bridge.historyClient() }
    factory { bridge.dashboards() }
    factory { bridge.flows() }
    factory { bridge.climate() }
    factory { bridge.transport() }
    factory { bridge.pushStatus() }
}

/** Starts (or restarts, if a previous Application in this process started it) the app's Koin graph. */
fun startNidooKoin(app: Application, bridge: KoinBridgeEntryPoint) {
    stopKoin()
    startKoin {
        androidContext(app)
        modules(hiltBridgeModule(bridge), onboardingModule, homeModule, settingsModule, androidSettingsModule, mainTabsModule)
    }
}
