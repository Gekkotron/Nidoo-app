package com.gekkotron.nidoo.feature.onboarding.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
internal actual fun rememberCameraAccess(): CameraAccess {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(if (granted()) CameraPermission.GRANTED else CameraPermission.WAITING) }
    // The user may grant the permission in Settings ("Open settings") and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission = permission.onResume(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permission = if (ok) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.WAITING) launcher.launch(Manifest.permission.CAMERA)
    }
    return CameraAccess(permission) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
