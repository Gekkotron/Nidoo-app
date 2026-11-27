package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsViewModel
import com.gekkotron.nidoo.feature.settings.resources.Res
import com.gekkotron.nidoo.feature.settings.resources.voice_permission_denied_snackbar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Voice page on iOS: the toggle, and a snackbar when the permission prompt is refused. */
@Composable
fun VoiceSettingsPage(snackbar: SnackbarHostState, viewModel: VoiceSettingsViewModel = koinViewModel()) {
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val denied by viewModel.denied.collectAsStateWithLifecycle()
    val deniedMessage = stringResource(Res.string.voice_permission_denied_snackbar)
    LaunchedEffect(denied) {
        if (denied) {
            viewModel.deniedShown()
            snackbar.showSnackbar(deniedMessage)
        }
    }
    VoiceSection(voice, viewModel::setVoiceEnabled, showTitle = false)
}
