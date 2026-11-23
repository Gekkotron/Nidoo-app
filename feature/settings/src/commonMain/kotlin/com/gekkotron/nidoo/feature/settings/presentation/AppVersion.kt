package com.gekkotron.nidoo.feature.settings.presentation

import androidx.compose.runtime.Composable

/** "0.12", or "0.12 · debug" on a debug build: the package version on Android, the bundle's short version on iOS. */
@Composable
expect fun rememberAppVersion(): String
