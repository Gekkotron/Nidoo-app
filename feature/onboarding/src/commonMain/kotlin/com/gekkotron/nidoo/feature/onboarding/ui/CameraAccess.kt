package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal enum class CameraPermission { WAITING, GRANTED, DENIED }

/** Back from the system settings (ON_RESUME): a permission granted there shows the camera; otherwise nothing changes. */
internal fun CameraPermission.onResume(granted: Boolean): CameraPermission = if (granted) CameraPermission.GRANTED else this

/** The camera permission as the scan screen sees it, and how to reach the system settings when it was refused. */
internal class CameraAccess(val permission: CameraPermission, val openSettings: () -> Unit)

/** Asks once on first composition when the answer is not known yet, and re-checks on every return to the app. */
@Composable
internal expect fun rememberCameraAccess(): CameraAccess

/** The back camera's preview, calling [onText] for every decoded QR code (repeats included: the ViewModel filters). */
@Composable
internal expect fun QrCamera(modifier: Modifier, onText: (String) -> Unit)

/** Keeps secrets out of screenshots and the app switcher while it is shown, where the platform allows it (spec §8). */
@Composable
internal expect fun SecureWindow()
