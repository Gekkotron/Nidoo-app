package com.gekkotron.nidoo.feature.voice.di

import com.gekkotron.nidoo.feature.voice.VoiceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Voice's ViewModel. Each app supplies `AppPreferences`, `AssistClient`, `AudioPlayer` and the `SpeechCaptureFactory`. */
val voiceModule: Module = module {
    viewModel { VoiceViewModel(get(), get(), get(), get()) }
}
