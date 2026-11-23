package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.format.FormatLocale
import com.gekkotron.nidoo.format.systemLocale

@Composable
internal actual fun currentFormatLocale(): FormatLocale = systemLocale()
