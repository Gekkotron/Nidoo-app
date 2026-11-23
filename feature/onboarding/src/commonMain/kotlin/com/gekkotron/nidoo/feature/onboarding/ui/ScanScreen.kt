package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.data.PayloadError
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.onboarding.resources.*

@Composable
internal fun ScanScreen(
    error: PayloadError?,
    onText: (String) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    camera: @Composable (Modifier, (String) -> Unit) -> Unit = { modifier, callback -> QrCamera(modifier, callback) },
) {
    val access = rememberCameraAccess()
    ScanContent(
        permission = access.permission,
        error = error,
        onText = onText,
        onManual = onManual,
        onOpenSettings = access.openSettings,
        onBack = onBack,
        camera = camera,
    )
}

@Composable
internal fun ScanContent(
    permission: CameraPermission,
    error: PayloadError?,
    onText: (String) -> Unit,
    onManual: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    camera: @Composable (Modifier, (String) -> Unit) -> Unit = { modifier, callback -> QrCamera(modifier, callback) },
) {
    SecureWindow()
    OnboardingColumn {
        Text(stringResource(Res.string.scan_title), style = NidooTheme.type.display)
        when (permission) {
            CameraPermission.GRANTED -> camera(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)), onText)
            CameraPermission.WAITING -> Text(stringResource(Res.string.scan_waiting), style = NidooTheme.type.body)
            CameraPermission.DENIED -> {
                Text(stringResource(Res.string.scan_denied), style = NidooTheme.type.body)
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.scan_open_settings)) }
            }
        }
        if (error != null) {
            Text(
                payloadErrorText(error),
                style = NidooTheme.type.body,
                color = NidooTheme.colors.warning,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.welcome_manual)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.common_back)) }
    }
}
