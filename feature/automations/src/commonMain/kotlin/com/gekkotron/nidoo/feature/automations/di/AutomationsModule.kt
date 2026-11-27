package com.gekkotron.nidoo.feature.automations.di

import com.gekkotron.nidoo.feature.automations.presentation.AutomationsViewModel
import com.gekkotron.nidoo.source.EntitySource
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Automations' ViewModel. Each app supplies `FlowsRepository` and `EntitySource`. Spelled out, not `viewModelOf`:
 * the class has two constructors and Koin would not know which to use.
 */
val automationsModule: Module = module {
    viewModel { AutomationsViewModel(get(), get<EntitySource>()) }
}
