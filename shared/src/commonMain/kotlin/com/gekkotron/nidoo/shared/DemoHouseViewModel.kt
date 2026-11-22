package com.gekkotron.nidoo.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The iOS root's demo line: null until the demo house has been discovered, then "Demo house: n entities". */
class DemoHouseViewModel(
    summary: suspend (CoroutineScope) -> String = ::demoHouseSummary,
) : ViewModel() {
    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    init {
        viewModelScope.launch { _summary.value = summary(this) }
    }
}
