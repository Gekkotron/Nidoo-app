package com.gekkotron.nidoo.feature.security.di

import com.gekkotron.nidoo.feature.security.presentation.SecurityViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Security's ViewModel. Each app supplies `EntitySource` and `HistoryClient`. Spelled out, not `viewModelOf`:
 * the constructor has defaults that Koin would not use.
 */
val securityModule: Module = module {
    viewModel { SecurityViewModel(source = get(), dispatcher = Dispatchers.Default, history = get()) }
}
