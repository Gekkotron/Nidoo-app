package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.shared.DemoHouseViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The common Koin graph (iOS spec §7.3). Phase 4 adds the shared screens' ViewModels here. */
val sharedModule = module {
    viewModel { DemoHouseViewModel() }
}
