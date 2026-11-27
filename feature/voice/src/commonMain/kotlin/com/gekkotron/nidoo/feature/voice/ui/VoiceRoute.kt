package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.gekkotron.nidoo.feature.voice.VoicePermissions
import com.gekkotron.nidoo.feature.voice.VoiceViewModel
import com.gekkotron.nidoo.feature.voice.awaitVoiceEnabled
import org.koin.compose.viewmodel.koinViewModel

/**
 * The voice sheet with its session: waits for the opt-in preference, asks [permissions], then listens. Off shows the
 * "turn it on in Settings" sheet; a refusal shows the permission error. The session is cancelled when the sheet leaves.
 */
@Composable
fun VoiceRoute(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    permissions: VoicePermissions,
    viewModel: VoiceViewModel = koinViewModel(),
) {
    LaunchedEffect(viewModel) {
        if (!awaitVoiceEnabled(viewModel.preferences.voiceEnabled)) {
            viewModel.showDisabled()
        } else if (permissions.request()) {
            if (!viewModel.started) viewModel.start()
        } else {
            viewModel.showPermissionDenied()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    VoiceSheet(
        state = viewModel.state,
        closeRequested = viewModel.closeRequested,
        canRepeat = viewModel.canRepeat,
        onChoose = viewModel::choose,
        onConfirm = viewModel::confirm,
        onListenAgain = viewModel::listenAgain,
        onRepeat = viewModel::repeat,
        onCancel = onClose,
        onOpenSettings = onOpenSettings,
    )
}
