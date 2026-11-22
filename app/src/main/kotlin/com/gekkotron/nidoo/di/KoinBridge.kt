package com.gekkotron.nidoo.di

import android.app.Application
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.source.EntitySource
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
}

/** Each type is a factory that asks Hilt, so Hilt keeps owning the lifetimes (EntitySource is unscoped there). */
fun hiltBridgeModule(bridge: KoinBridgeEntryPoint): Module = module {
    factory { bridge.profiles() }
    factory { bridge.preferences() }
    factory { bridge.entitySource() }
    factory { bridge.connectionTester() }
}

/** Starts (or restarts, if a previous Application in this process started it) the app's Koin graph. */
fun startNidooKoin(app: Application, bridge: KoinBridgeEntryPoint) {
    stopKoin()
    startKoin {
        androidContext(app)
        modules(hiltBridgeModule(bridge), onboardingModule)
    }
}
