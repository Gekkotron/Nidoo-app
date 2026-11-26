package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.shared.tabs.MainTabsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The tab bar's status ViewModel; the platform supplies `EntitySource`. */
val mainTabsModule: Module = module {
    viewModelOf(::MainTabsViewModel)
}
