package com.gekkotron.nidoo.designsystem

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
