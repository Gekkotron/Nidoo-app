package com.gekkotron.nidoo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.shared.resources.Res
import com.gekkotron.nidoo.shared.resources.house_set_up_again
import com.gekkotron.nidoo.shared.resources.interim_settings_back
import com.gekkotron.nidoo.shared.resources.interim_settings_title
import org.jetbrains.compose.resources.stringResource

/** Stands in for the Settings screen until 4C: only "Set up again". */
@Composable
internal fun InterimSettings(onSetUpAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.interim_settings_back)) }
        Text(stringResource(Res.string.interim_settings_title), style = NidooTheme.type.display, color = NidooTheme.colors.content)
        OutlinedButton(onClick = onSetUpAgain) { Text(stringResource(Res.string.house_set_up_again)) }
    }
}
