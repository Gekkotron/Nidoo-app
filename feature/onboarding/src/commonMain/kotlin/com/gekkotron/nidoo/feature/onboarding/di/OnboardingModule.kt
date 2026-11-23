package com.gekkotron.nidoo.feature.onboarding.di

import com.gekkotron.nidoo.feature.onboarding.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Onboarding's ViewModel; each app supplies `ProfileRepository` and `ConnectionTester` (Hilt's bridge, the iOS module). */
val onboardingModule: Module = module {
    viewModelOf(::OnboardingViewModel)
}
