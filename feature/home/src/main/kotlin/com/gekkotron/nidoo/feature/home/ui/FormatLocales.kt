package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.gekkotron.nidoo.format.FormatLocale

/** The locale the screen formats numbers and dates with: the first of the user's configured locales. */
@Composable
internal fun currentFormatLocale(): FormatLocale = LocalConfiguration.current.locales[0]
