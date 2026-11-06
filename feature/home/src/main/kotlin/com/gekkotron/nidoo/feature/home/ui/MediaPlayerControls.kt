package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.feature.home.presentation.mediaProgress
import com.gekkotron.nidoo.feature.home.presentation.mediaToggle
import com.gekkotron.nidoo.model.Command
import kotlinx.coroutines.delay

/** The sheet's line under the name: playing, paused or idle. */
@Composable
internal fun mediaStateText(state: String?): String = stringResource(
    when (state) {
        "playing" -> R.string.media_state_playing
        "paused" -> R.string.media_state_paused
        else -> R.string.media_state_idle
    },
)

/**
 * A media player's sheet (media player spec, Android "Control sheet"): the large cover, the title, "artist · album",
 * ⏮ ▶/❚❚ ⏭ as the player allows and the progress line. No volume (the satellites have it) and no search (voice only).
 */
@Composable
internal fun MediaPlayerControls(card: CardUi, onSend: (Command) -> Unit) {
    val value = card.value as? CardValue.MediaPlayer ?: return
    val features = card.capabilities.mediaFeatures
    val idle = value.state == "idle" || value.state == null
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaCover(value.imageUrl.takeIf { !idle }, 200.dp)
        if (idle) {
            Text(stringResource(R.string.media_nothing_playing), style = NidooTheme.type.body)
            if ("play" in features) {
                FilledTonalButton(onClick = { onSend(Command.MediaAction("play")) }, enabled = card.available) {
                    Text(stringResource(R.string.media_play_some))
                }
            }
            return@Column
        }
        value.title?.let {
            Text(
                it,
                style = NidooTheme.type.display.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        listOfNotNull(value.artist, value.album).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = NidooTheme.type.body,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MediaProgressLine(value)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if ("previous" in features) MediaSkipButton("previous", card.available, onSend)
            mediaToggle(value.state, features)?.let { action ->
                MediaToggleButton(action, enabled = card.available, onSend = onSend, modifier = Modifier.size(64.dp), iconSize = 40.dp)
            }
            if ("next" in features) MediaSkipButton("next", card.available, onSend)
        }
    }
}

/**
 * On a tinted sheet the theme's own colours (History and Customize take `primary`, the drag handle `onSurfaceVariant`)
 * can vanish on the cover colour, Ivoire's near-black worst of all: they turn white like the rest of the sheet.
 */
@Composable
internal fun TintedSheetColors(tint: Color?, content: @Composable () -> Unit) {
    if (tint == null) return content()
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = scheme.copy(primary = Color.White, onSurface = Color.White, onSurfaceVariant = Color.White.copy(alpha = 0.7f)),
        content = content,
    )
}

/** The song's progress, re-read every second while playing; nothing without a duration. */
@Composable
private fun MediaProgressLine(value: CardValue.MediaPlayer) {
    val playing = value.state == "playing"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing, value.positionS, value.positionAtMs) {
        now = System.currentTimeMillis()
        while (playing) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val progress = mediaProgress(value.durationS, value.positionS, value.positionAtMs, playing, now) ?: return
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().testTag("media-progress"),
        color = LocalContentColor.current,
        trackColor = LocalContentColor.current.copy(alpha = 0.24f),
    )
}

@Composable
private fun MediaSkipButton(action: String, enabled: Boolean, onSend: (Command) -> Unit) {
    val label = stringResource(if (action == "next") R.string.media_next else R.string.media_previous)
    IconButton(
        onClick = { onSend(Command.MediaAction(action)) },
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        StaticIcon(if (action == "next") NidooGlyph.NEXT else NidooGlyph.PREVIOUS, modifier = Modifier.size(28.dp))
    }
}
