package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.format.FormatLocale

/** The locale the screen formats numbers and dates with: the user's first configured locale. */
@Composable
internal expect fun currentFormatLocale(): FormatLocale
