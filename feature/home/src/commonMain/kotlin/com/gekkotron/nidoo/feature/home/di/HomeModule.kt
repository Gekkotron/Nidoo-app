package com.gekkotron.nidoo.feature.home.di

import com.gekkotron.nidoo.feature.home.presentation.CustomizeViewModel
import com.gekkotron.nidoo.feature.home.presentation.DashboardViewModel
import com.gekkotron.nidoo.feature.home.presentation.HomeViewModel
import com.gekkotron.nidoo.feature.home.presentation.LabelText
import com.gekkotron.nidoo.feature.home.rooms.RoomsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Home's ViewModels and its entity-label translations. Each app supplies `EntitySource`, `AppPreferences`,
 * `HistoryClient`, `DashboardsRepository`, `FlowsRepository`, `ClimateRepository` and `MqttTransport` (Hilt's
 * bridge on Android, the iOS module). The ViewModels are spelled out, not `viewModelOf`: their constructors have
 * defaults that Koin would not use.
 */
val homeModule: Module = module {
    single<LabelText> { PreloadedLabelText(CoroutineScope(SupervisorJob() + Dispatchers.Default)) } onClose { (it as? PreloadedLabelText)?.close() }
    viewModel {
        HomeViewModel(
            source = get(),
            savedState = get(),
            defaultDispatcher = Dispatchers.Default,
            labels = get(),
            history = get(),
            preferences = get(),
            climate = get(),
            transport = get(),
        )
    }
    viewModel {
        DashboardViewModel(
            source = get(),
            dashboards = get(),
            flows = get(),
            preferences = get(),
            history = get(),
            labels = get(),
            dispatcher = Dispatchers.Default,
        )
    }
    viewModel { CustomizeViewModel(source = get(), labels = get(), history = get()) }
    viewModel { RoomsViewModel(source = get(), savedState = get(), defaultDispatcher = Dispatchers.Default) }
}
