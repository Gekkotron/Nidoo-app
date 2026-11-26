package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.gekkotron.nidoo.format.FormatLocale

// Read from the configuration, not Locale.getDefault(), so a locale change recomposes the screen.
@Composable
internal actual fun currentFormatLocale(): FormatLocale = LocalConfiguration.current.locales[0]
