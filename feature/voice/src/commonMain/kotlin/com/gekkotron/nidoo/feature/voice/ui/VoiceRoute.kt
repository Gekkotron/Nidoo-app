package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.gekkotron.nidoo.feature.voice.VoicePermissions
import com.gekkotron.nidoo.feature.voice.VoiceViewModel
import com.gekkotron.nidoo.feature.voice.awaitVoiceEnabled
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    suspend fun askThenListen() {
        if (permissions.request()) {
            if (!viewModel.started) viewModel.start()
        } else {
            viewModel.showPermissionDenied()
        }
    }
    LaunchedEffect(viewModel) {
        if (!awaitVoiceEnabled(viewModel.preferences.voiceEnabled)) viewModel.showDisabled() else askThenListen()
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    VoiceSheet(
        state = viewModel.state,
        closeRequested = viewModel.closeRequested,
        canRepeat = viewModel.canRepeat,
        onChoose = viewModel::choose,
        onConfirm = viewModel::confirm,
        // After a refusal there is no session to restart: "Try again" asks for the permissions again.
        onListenAgain = { if (viewModel.started) viewModel.listenAgain() else scope.launch { askThenListen() } },
        onRepeat = viewModel::repeat,
        onCancel = onClose,
        onOpenSettings = onOpenSettings,
    )
}
