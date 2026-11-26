package com.gekkotron.nidoo.feature.energy.di

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.energy.history.EnergyHistoryImpl
import com.gekkotron.nidoo.feature.energy.presentation.EnergyViewModel
import com.gekkotron.nidoo.history.EnergyHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Energy's ViewModel and its [EnergyHistory]. Each app supplies `EntitySource`, `HistoryClient` and
 * `AppPreferences` (Hilt's bridge on Android, the iOS module). The ViewModel is spelled out, not `viewModelOf`:
 * its constructor has defaults that Koin would not use.
 */
val energyModule: Module = module {
    // A factory: EntitySource is unscoped (a new profile gets a new source), so the history must not pin the first.
    factory<EnergyHistory> { EnergyHistoryImpl(get(), get()) }
    viewModel {
        EnergyViewModel(
            source = get(),
            dispatcher = Dispatchers.Default,
            history = get(),
            energyView = get<AppPreferences>().energyView.filterNotNull(),
            clock = { Clock.System.now().toEpochMilliseconds() },
        )
    }
}
