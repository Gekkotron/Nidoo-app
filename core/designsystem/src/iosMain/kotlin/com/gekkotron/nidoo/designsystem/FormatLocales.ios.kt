package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.format.FormatLocale
import com.gekkotron.nidoo.format.systemLocale

@Composable
actual fun currentFormatLocale(): FormatLocale = systemLocale()
