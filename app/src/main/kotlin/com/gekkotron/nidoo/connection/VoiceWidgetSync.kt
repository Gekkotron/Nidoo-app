package com.gekkotron.nidoo.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the voice toggle flips: the "Nidoo voice" widget switches between its mic and its "Voice off"
 * state, and the devices widget shows or hides its mic. Glance only redraws on request, so without this an
 * already-placed widget kept its old look until its next data refresh.
 */
class VoiceWidgetSync(
    private val voiceEnabled: Flow<Boolean?>,
    private val update: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            // The first real value is what the widgets already drew from; only later changes need a redraw.
            voiceEnabled.filterNotNull().distinctUntilChanged().drop(1).collect { update() }
        }
    }
}
