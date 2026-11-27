package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.shared.tabs.MainTabsViewModel
import com.gekkotron.nidoo.source.EntitySource
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The tab bar's status ViewModel; the platform supplies `ProfileRepository` and `EntitySource` (a factory, re-asked per profile). */
val mainTabsModule: Module = module {
    viewModel { MainTabsViewModel(get<ProfileRepository>().state) { get<EntitySource>() } }
}
