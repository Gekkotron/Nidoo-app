package com.gekkotron.nidoo.feature.settings.di

import com.gekkotron.nidoo.feature.settings.presentation.AndroidPlatformSettings
import com.gekkotron.nidoo.feature.settings.presentation.AndroidSettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.PlatformSettings
import com.gekkotron.nidoo.feature.settings.presentation.PresenceSettingsViewModel
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Android's half of Settings: push, presence and voice. Needs `PushStatusSource` and `AppPreferences` from the app's
 * graph, and the Android context from `androidContext()`.
 */
val androidSettingsModule: Module = module {
    factory<SpeechAvailability.Status> { SpeechAvailability.check(androidContext()) }
    factory<PlatformSettings> { AndroidPlatformSettings(get(), get(), get()) }
    viewModel { AndroidSettingsViewModel(get(), get(), get()) }
    viewModel { PresenceSettingsViewModel(get()) }
}
