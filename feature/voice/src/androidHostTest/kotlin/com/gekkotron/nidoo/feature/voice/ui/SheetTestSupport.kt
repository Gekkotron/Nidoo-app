package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sheet with every callback defaulted, for tests that only look at one thing. */
@Composable
internal fun sheet(
    state: StateFlow<VoiceSessionState>,
    closeRequested: StateFlow<Boolean> = MutableStateFlow(false),
    canRepeat: StateFlow<Boolean> = MutableStateFlow(false),
    onChoose: (Int) -> Unit = {},
    onConfirm: (Boolean) -> Unit = {},
    onListenAgain: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onCancel: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) = VoiceSheet(state, closeRequested, canRepeat, onChoose, onConfirm, onListenAgain, onRepeat, onCancel, onOpenSettings)
