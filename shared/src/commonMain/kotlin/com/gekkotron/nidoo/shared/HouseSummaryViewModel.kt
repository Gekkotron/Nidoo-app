package com.gekkotron.nidoo.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.connection.deviceNames
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.LinkState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the house screen shows until Home lands on iOS (plan 4B): the link, and how many devices the hub announced. */
data class HouseSummary(val link: LinkState, val devices: Int)

class HouseSummaryViewModel(source: EntitySource) : ViewModel() {
    /** [HouseSummary.devices] counts as the onboarding test does: devices, plus entities that belong to none. */
    val summary: StateFlow<HouseSummary> = combine(source.link, source.devices, source.entities) { link, devices, entities ->
        HouseSummary(link, deviceNames(devices, entities).size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseSummary(source.link.value, 0))
}
