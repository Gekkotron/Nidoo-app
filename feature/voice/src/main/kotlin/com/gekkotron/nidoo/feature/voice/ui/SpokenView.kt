package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.voice.R

@Composable
internal fun ColumnScope.SpokenView(text: String, speakable: Boolean, onRepeat: () -> Unit) {
    Text(text, style = NidooTheme.type.body, color = NidooTheme.colors.content, textAlign = TextAlign.Center)
    if (speakable) VoiceRow(stringResource(R.string.voice_repeat), onRepeat)
}
