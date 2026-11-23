package com.gekkotron.nidoo.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gekkotron.nidoo.designsystem.permissions.openNotificationSettings
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.feature.settings.presentation.AndroidSettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The body of the Android-only Settings pages: Notifications (push), Presence and Voice. Any other page renders
 * nothing. The nav host fills `SettingsPageScreen`'s `platformPage` slot with it.
 */
@Composable
fun AndroidSettingsPage(
    page: SettingsPage,
    snackbar: SnackbarHostState,
    viewModel: AndroidSettingsViewModel = hiltViewModel(),
    clock: () -> Instant = Instant::now,
    presenceSection: @Composable (onInfo: (String) -> Unit) -> Unit = { info -> PresenceSettingsSlot(onInfoMessage = info, showTitle = false) },
) {
    when (page) {
        SettingsPage.NOTIFICATIONS -> NotificationsPage(viewModel, clock)
        SettingsPage.PRESENCE -> {
            val scope = rememberCoroutineScope()
            presenceSection { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        }
        SettingsPage.VOICE -> VoicePage(viewModel, snackbar)
        else -> Unit
    }
}

@Composable
private fun NotificationsPage(viewModel: AndroidSettingsViewModel, clock: () -> Instant) {
    val status by viewModel.push.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The "N min ago" text must move on without a new status emission, so the clock is state here. Only this page
    // shows the push status, so only it ticks and re-reads it (settings sub-pages spec).
    var now by remember { mutableStateOf(clock()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(60_000)
                now = clock()
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        now = clock()
        viewModel.refreshPush()
        onPauseOrDispose {}
    }
    NotificationsBody(status, now, viewModel::registerAgain) { openNotificationSettings(context) }
}

/** The voice toggle asks for the microphone first; a refusal turns voice off and says why. */
@Composable
private fun VoicePage(viewModel: AndroidSettingsViewModel, snackbar: SnackbarHostState) {
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deniedMessage = stringResource(R.string.voice_permission_denied_snackbar)
    var micDenied by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setVoiceEnabled(true) else { viewModel.setVoiceEnabled(false); micDenied = true }
    }
    LaunchedEffect(micDenied) {
        if (micDenied) {
            snackbar.showSnackbar(deniedMessage)
            micDenied = false
        }
    }
    val onVoiceToggle: (Boolean) -> Unit = { on ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!on || granted) viewModel.setVoiceEnabled(on) else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    VoiceSection(voice, onVoiceToggle, showTitle = false)
}
