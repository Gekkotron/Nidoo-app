package com.gekkotron.nidoo.feature.settings.di

import com.gekkotron.nidoo.feature.settings.presentation.SettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsViewModel
import com.gekkotron.nidoo.feature.settings.rooms.RoomOrderViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Settings' ViewModels. Each app supplies `ProfileRepository`, `AppPreferences`, `EntitySource` and a
 * `PlatformSettings` (Android's own, or `NoPlatformSettings` on iOS).
 */
val settingsModule: Module = module {
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { RoomOrderViewModel(get()) }
    // iOS's Voice page; Android's lives in AndroidSettingsViewModel. Needs AppPreferences, a SpeechAvailability.Status and VoicePermissions.
    viewModel { VoiceSettingsViewModel(get(), get(), get()) }
}
