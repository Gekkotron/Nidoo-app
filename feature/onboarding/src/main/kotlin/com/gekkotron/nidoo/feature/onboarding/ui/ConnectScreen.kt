package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.onboarding.R
import com.gekkotron.nidoo.feature.onboarding.TestState

private const val MAX_DEVICES_SHOWN = 5

@Composable
internal fun ConnectScreen(
    test: TestState,
    saving: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    saveFailed: Boolean = false,
) {
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    OnboardingColumn {
        when (test) {
            TestState.Connecting -> {
                Text(stringResource(R.string.connect_connecting), style = NidooTheme.type.display, modifier = announce)
                CircularProgressIndicator(color = NidooTheme.colors.content)
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_back)) }
            }
            is TestState.Failed -> {
                Text(stringResource(R.string.connect_failed), style = NidooTheme.type.display)
                Text(testFailureText(test.failure), style = NidooTheme.type.body, modifier = announce)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_retry)) }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_back)) }
            }
            is TestState.Connected -> {
                val count = test.devices.size
                Text(pluralStringResource(R.plurals.connect_discovering, count, count), style = NidooTheme.type.display, modifier = announce)
                if (test.viaGateway) {
                    Text(stringResource(R.string.connect_via_gateway), style = NidooTheme.type.body, color = NidooTheme.colors.contentMuted)
                }
                if (count == 0) {
                    Text(stringResource(R.string.connect_no_devices), style = NidooTheme.type.body, color = NidooTheme.colors.contentMuted)
                }
                test.devices.take(MAX_DEVICES_SHOWN).forEach { Text(it, style = NidooTheme.type.body) }
                val more = count - MAX_DEVICES_SHOWN
                if (more > 0) {
                    Text(
                        pluralStringResource(R.plurals.connect_more_devices, more, more),
                        style = NidooTheme.type.body,
                        color = NidooTheme.colors.contentMuted,
                    )
                }
                if (saveFailed) SaveFailedText()
                Button(onClick = onContinue, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_continue)) }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_back)) }
            }
        }
    }
}
