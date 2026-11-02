package com.gekkotron.nidoo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.navigation.NidooNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The same instance `hiltViewModel()` hands to the composition below: both use this activity's store. */
    private val appViewModel: AppViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // Every return to the app, throttled by UpdateCheckGate (release only; a no-op in debug).
        TesterUpdates.check(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            // A rotation re-delivers the same intent: only a fresh launch is a tap on a notification.
            handleIntent(intent)
        }
        setContent {
            val app: AppViewModel = hiltViewModel()
            val theme by app.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeChoice.NIGHT_GLASS -> true
                ThemeChoice.IVOIRE -> false
                ThemeChoice.SYSTEM, null -> isSystemInDarkTheme()
            }
            // The bar icons follow the chosen theme, not the system one (the default style would).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            NidooTheme(theme ?: ThemeChoice.SYSTEM) {
                // Until the preferences have loaded only the background is drawn, so Home never flashes in the wrong theme.
                NidooBackground { if (theme != null) NidooNavHost(app) }
            }
        }
    }

    /** `singleTop`: a tap on a notification while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        DeepLinks.linkToOpen(intent)?.let(appViewModel::openLink)
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
