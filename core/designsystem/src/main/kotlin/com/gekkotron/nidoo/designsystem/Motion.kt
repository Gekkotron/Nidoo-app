package com.gekkotron.nidoo.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * False when the user turned animations off (Accessibility › Remove animations sets the animator duration
 * scale to 0): pulses and spins are then drawn still. Previews always animate.
 */
@Composable
fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}
