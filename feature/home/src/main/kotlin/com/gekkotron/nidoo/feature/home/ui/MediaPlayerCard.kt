package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.feature.home.presentation.mediaToggle
import com.gekkotron.nidoo.model.Command

/**
 * The cover, or the note while it loads, fails or is missing. Decorative: the title and artist say what plays.
 * Covers come through the app's Coil loader, whose HTTP fetcher handles them (alerts use their own request type).
 */
@Composable
internal fun MediaCover(imageUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 6)
    val note = @Composable {
        Box(Modifier.fillMaxSize().background(LocalContentColor.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            StaticIcon(NidooGlyph.MUSIC, modifier = Modifier.size(size / 2))
        }
    }
    Box(modifier.size(size).clip(shape).clearAndSetSemantics { }) {
        if (imageUrl == null) {
            note()
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { note() },
                error = { note() },
            )
        }
    }
}

/** Play or pause, as the player allows: [action] is "play" or "pause". */
@Composable
internal fun MediaToggleButton(action: String, enabled: Boolean, onSend: (Command) -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    val label = stringResource(if (action == "pause") R.string.media_pause else R.string.media_play)
    IconButton(
        onClick = { onSend(Command.MediaAction(action)) },
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        StaticIcon(if (action == "pause") NidooGlyph.PAUSE else NidooGlyph.PLAY, modifier = Modifier.size(iconSize))
    }
}

/** The top row's leading slot on a media player card: the cover thumbnail, or the note when nothing plays. */
@Composable
internal fun MediaCardCover(value: CardValue.MediaPlayer) {
    MediaCover(value.imageUrl.takeIf { value.state != "idle" }, 40.dp)
}

/** The top row's trailing button: ❚❚ while playing, ▶ otherwise (▶ on an idle player plays something). */
@Composable
internal fun MediaCardButton(card: CardUi, value: CardValue.MediaPlayer, onSend: (Command) -> Unit) {
    mediaToggle(value.state, card.capabilities.mediaFeatures)?.let { action ->
        MediaToggleButton(action, enabled = card.available, onSend = onSend)
    }
}

/**
 * The card body below the name (media player spec, Android "Home card"): the title, then the artist; an idle player
 * says "Nothing playing". [tinted] tags the body so tests can tell the cover colour reached the card.
 */
@Composable
internal fun MediaPlayerDetails(value: CardValue.MediaPlayer, valueText: String, tinted: Boolean) {
    Column(if (tinted) Modifier.testTag("media-tint") else Modifier) {
        val title = value.title.takeIf { value.state != "idle" }
        Text(title ?: valueText, style = NidooTheme.type.cardValue, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value.artist?.takeIf { title != null }?.let { artist ->
            Text(
                artist,
                style = NidooTheme.type.label,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
