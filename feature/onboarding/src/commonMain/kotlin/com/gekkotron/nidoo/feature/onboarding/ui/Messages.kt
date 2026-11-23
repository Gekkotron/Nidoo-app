package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.gekkotron.nidoo.data.PayloadError
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.onboarding.resources.*
import com.gekkotron.nidoo.connection.TestFailure

@Composable
internal fun payloadErrorText(error: PayloadError): String = when (error) {
    PayloadError.NotNidooCode -> stringResource(Res.string.scan_error_not_nidoo)
    is PayloadError.UnsupportedVersion -> stringResource(Res.string.scan_error_version, error.version)
    is PayloadError.InvalidField -> stringResource(Res.string.scan_error_field, error.field)
}

/** Shown when saving the profile failed; announced like every other error. */
@Composable
internal fun SaveFailedText() {
    Text(
        stringResource(Res.string.save_failed),
        style = NidooTheme.type.body,
        color = NidooTheme.colors.warning,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun testFailureText(failure: TestFailure): String = when (failure) {
    TestFailure.Unreachable -> stringResource(Res.string.connect_error_unreachable)
    TestFailure.Rejected -> stringResource(Res.string.connect_error_rejected)
    TestFailure.Tls -> stringResource(Res.string.connect_error_tls)
    TestFailure.KeyRejected -> stringResource(Res.string.connect_error_key_rejected)
    TestFailure.ClockSkew -> stringResource(Res.string.connect_error_clock)
    TestFailure.GatewayRefused -> stringResource(Res.string.connect_error_gateway_refused)
    TestFailure.InvalidGateway -> stringResource(Res.string.connect_error_invalid_gateway)
    is TestFailure.Other -> stringResource(Res.string.connect_error_other, failure.message)
}
