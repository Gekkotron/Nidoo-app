package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.shared.AppShellViewModel
import com.gekkotron.nidoo.shared.HouseSummaryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The shell's ViewModels; the platform module supplies the repositories, the session and `EntitySource`. */
val sharedModule: Module = module {
    viewModelOf(::AppShellViewModel)
    viewModelOf(::HouseSummaryViewModel)
}
