package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

@Composable
internal actual fun rememberCameraAccess(): CameraAccess {
    var permission by remember { mutableStateOf(cameraPermission()) }
    // Back from the Settings app: a permission granted there shows the camera without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission = permission.onResume(cameraPermission() == CameraPermission.GRANTED) }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.WAITING) {
            // iOS shows its prompt (with NSCameraUsageDescription) once; afterwards only the Settings app can change it.
            val ok = suspendCancellableCoroutine { done ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted -> done.resume(granted) }
            }
            permission = if (ok) CameraPermission.GRANTED else CameraPermission.DENIED
        }
    }
    return CameraAccess(permission, ::openAppSettings)
}

private fun cameraPermission(): CameraPermission = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
    AVAuthorizationStatusAuthorized -> CameraPermission.GRANTED
    AVAuthorizationStatusNotDetermined -> CameraPermission.WAITING
    else -> CameraPermission.DENIED // denied, or restricted by Screen Time / a profile
}

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
