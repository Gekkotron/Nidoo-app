package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.runtime.Composable

/** iOS has no public equivalent of FLAG_SECURE; the setup code is only on screen while it is scanned. */
@Composable
internal actual fun SecureWindow() {}
