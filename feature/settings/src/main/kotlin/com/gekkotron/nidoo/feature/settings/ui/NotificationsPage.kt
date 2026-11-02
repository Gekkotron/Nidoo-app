package com.gekkotron.nidoo.feature.settings.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.push.PushStatus
import java.time.Duration
import java.time.Instant

/** The push status in words (push alerts spec §7.4). */
@Composable
internal fun pushStatusText(status: PushStatus, now: Instant): String = when (status) {
    is PushStatus.Registered -> stringResource(R.string.settings_push_registered, ageText(status.at, now))
    PushStatus.Waiting -> stringResource(R.string.settings_push_waiting)
    is PushStatus.Failed -> stringResource(R.string.settings_push_failed, status.reason)
    PushStatus.NotificationsOff -> stringResource(R.string.settings_push_off)
    PushStatus.Demo -> stringResource(R.string.settings_push_demo)
}

@Composable
private fun ageText(at: Instant, now: Instant): String {
    if (Duration.between(at, now).seconds < 60) return stringResource(R.string.settings_push_just_now)
    return DateUtils.getRelativeTimeSpanString(
        at.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/** The Notifications page (push alerts spec §7.4): the status, Register again, and the system's channel settings. */
@Composable
internal fun NotificationsBody(status: PushStatus, now: Instant, onRegisterAgain: () -> Unit, onOpenChannels: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pushStatusText(status, now), style = NidooTheme.type.body)
        FilledTonalButton(onClick = onRegisterAgain, enabled = status != PushStatus.Demo, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_push_register_again))
        }
        OutlinedButton(onClick = onOpenChannels, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_push_channels))
        }
    }
}
