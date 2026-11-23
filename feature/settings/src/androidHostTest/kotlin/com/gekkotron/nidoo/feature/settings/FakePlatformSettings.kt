package com.gekkotron.nidoo.feature.settings

import com.gekkotron.nidoo.feature.settings.presentation.PlatformRow
import com.gekkotron.nidoo.feature.settings.presentation.PlatformSettings
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlatformSettings(initial: List<PlatformRow> = emptyList()) : PlatformSettings {
    val flow = MutableStateFlow(initial)
    var refreshes = 0

    override val rows = flow
    override fun currentRows() = flow.value
    override fun refresh() {
        refreshes++
    }
}
