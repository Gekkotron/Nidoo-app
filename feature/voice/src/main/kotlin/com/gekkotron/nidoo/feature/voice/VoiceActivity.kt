package com.gekkotron.nidoo.feature.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.voice.ui.VoiceSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceActivity : ComponentActivity() {
    private val viewModel: VoiceViewModel by viewModels()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // Off: say so (with a way to Settings) rather than closing on a tap that seemed to do nothing.
            val enabled = awaitVoiceEnabled(viewModel.preferences.voiceEnabled)
            if (!enabled) viewModel.showDisabled()
            // The user's theme, not the system one, so the sheet matches the app it opens over.
            val theme = viewModel.preferences.theme.filterNotNull().first().toChoice()
            setContent {
                val dark = when (theme) {
                    ThemeChoice.NIGHT_GLASS -> true
                    ThemeChoice.IVOIRE -> false
                    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                }
                // Transparent bars: the translucent window otherwise draws them black over the app behind.
                DisposableEffect(dark) {
                    val style = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                NidooTheme(theme) {
                    Surface(color = Color.Transparent) {
                        VoiceSheet(
                            state = viewModel.state,
                            closeRequested = viewModel.closeRequested,
                            canRepeat = viewModel.canRepeat,
                            onChoose = viewModel::choose,
                            onConfirm = viewModel::confirm,
                            onListenAgain = viewModel::listenAgain,
                            onRepeat = viewModel::repeat,
                            onCancel = { finish() },
                            onOpenSettings = ::openSettings,
                        )
                    }
                }
            }
            if (!enabled) return@launch
            if (ContextCompat.checkSelfPermission(this@VoiceActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoice()
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(Intent.ACTION_VIEW, SETTINGS_LINK.toUri()).setPackage(packageName))
        finish()
    }

    private fun startVoice() {
        if (!viewModel.started) viewModel.start()
    }

    override fun onDestroy() {
        viewModel.cancel()
        super.onDestroy()
    }
}

/** Parsed by `:app` (same link the widget's settings action uses). */
/** The voice sheet's "Open Settings" lands on the Voice page, where the toggle is. */
private const val SETTINGS_LINK = "nidoo://settings/voice"

private fun ThemePreference.toChoice(): ThemeChoice = when (this) {
    ThemePreference.SYSTEM -> ThemeChoice.SYSTEM
    ThemePreference.NIGHT_GLASS -> ThemeChoice.NIGHT_GLASS
    ThemePreference.IVOIRE -> ThemeChoice.IVOIRE
}
