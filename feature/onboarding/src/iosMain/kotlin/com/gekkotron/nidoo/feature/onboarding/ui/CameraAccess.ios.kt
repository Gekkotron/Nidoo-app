package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Placeholders until the AVFoundation scanner lands (plan 4A, Task 4).
@Composable
internal actual fun rememberCameraAccess(): CameraAccess = CameraAccess(CameraPermission.DENIED) {}

@Composable
internal actual fun QrCamera(modifier: Modifier, onText: (String) -> Unit) {}

@Composable
internal actual fun SecureWindow() {}
