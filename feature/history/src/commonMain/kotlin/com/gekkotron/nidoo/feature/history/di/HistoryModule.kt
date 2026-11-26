package com.gekkotron.nidoo.feature.history.di

import com.gekkotron.nidoo.feature.history.presentation.HistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * History's ViewModel. `EnergyHistory` comes from `energyModule`; each app supplies `EntitySource` and
 * `HistoryClient`. Spelled out, not `viewModelOf`, to match the other feature modules.
 */
val historyModule: Module = module {
    viewModel { HistoryViewModel(source = get(), history = get(), energy = get()) }
}
