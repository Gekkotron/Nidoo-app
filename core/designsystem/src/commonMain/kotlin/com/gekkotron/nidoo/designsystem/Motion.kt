package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable

/**
 * False when the user turned animations off (Android: Accessibility › Remove animations sets the animator duration
 * scale to 0; iOS: Reduce Motion): pulses and spins are then drawn still. Previews always animate.
 */
@Composable
expect fun animationsEnabled(): Boolean
