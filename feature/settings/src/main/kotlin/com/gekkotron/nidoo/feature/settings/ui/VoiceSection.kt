package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsUi
import com.gekkotron.nidoo.feature.voice.SpeechAvailability

@Composable
/** [showTitle] is off on the Voice page, whose top bar already says "Voice". */
fun VoiceSection(ui: VoiceSettingsUi, onToggle: (Boolean) -> Unit, showTitle: Boolean = true) {
    val ready = ui.availability == SpeechAvailability.Status.Ready
    val reason = when (ui.availability) {
        SpeechAvailability.Status.Ready -> null
        SpeechAvailability.Status.NeedsApi31 -> stringResource(R.string.voice_availability_needs_api_31)
        SpeechAvailability.Status.NoOnDevice -> stringResource(R.string.voice_availability_no_on_device)
        is SpeechAvailability.Status.LanguagePackMissing -> stringResource(R.string.voice_availability_language_pack_missing, ui.availability.tag)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) {
            Text(
                stringResource(R.string.voice_section_title),
                style = NidooTheme.type.label,
                color = NidooTheme.colors.contentMuted,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = NidooDimens.minTouch)
                .toggleable(value = ui.enabled && ready, enabled = ready, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.voice_toggle_label), style = NidooTheme.type.body, modifier = Modifier.weight(1f))
            Switch(checked = ui.enabled && ready, onCheckedChange = null, enabled = ready)
        }
        if (reason != null) Text(reason, style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted)
    }
}
