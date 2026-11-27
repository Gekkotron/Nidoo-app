package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable

/** True when the user's clock shows 24-hour time (Android: the system setting; iOS: the region's time format). */
@Composable
expect fun is24HourClock(): Boolean
