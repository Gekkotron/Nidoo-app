package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.voice.ErrorKind
import com.gekkotron.nidoo.feature.voice.R
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Tappable pill used for every action in the sheet. */
@Composable
internal fun VoiceRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = NidooTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.chip)
            .border(1.dp, c.chipBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = NidooTheme.type.body, color = if (danger) c.offline else c.content)
    }
}

@Composable
fun VoiceSheet(
    state: StateFlow<VoiceSessionState>,
    closeRequested: StateFlow<Boolean>,
    canRepeat: StateFlow<Boolean>,
    onChoose: (Int) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onListenAgain: () -> Unit,
    onRepeat: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val current = state.collectAsStateWithLifecycle().value
    val c = NidooTheme.colors
    // A touch keeps the sheet open ("unless touched"), but only for what was on screen when it happened: the tap that
    // answered a question must not keep the turn it leads to from closing.
    val shown: Any = when (current) {
        is VoiceSessionState.Turn -> current.turn.id
        else -> current::class
    }
    var touched by remember(shown) { mutableStateOf(false) }
    val closeNow = closeRequested.collectAsStateWithLifecycle().value
    val repeatable = canRepeat.collectAsStateWithLifecycle().value
    if (closeNow && !touched) {
        LaunchedEffect(Unit) { onCancel() }
    }
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onCancel,
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.backgroundBottom)
                .pointerInput(Unit) {
                    // Initial pass: seen before the buttons consume it.
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) touched = true
                        }
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                is VoiceSessionState.Listening -> ListeningView(current.partial)
                is VoiceSessionState.Sending -> SendingView(current.heard)
                is VoiceSessionState.Turn -> TurnView(
                    current.turn, current.expired, repeatable, onChoose, onConfirm, onListenAgain, onRepeat,
                )
                is VoiceSessionState.Error -> {
                    Text(
                        stringResource(errorRes(current.kind)),
                        style = NidooTheme.type.body,
                        color = c.content,
                        textAlign = TextAlign.Center,
                    )
                    when (current.kind) {
                        ErrorKind.DISABLED -> VoiceRow(stringResource(R.string.voice_open_settings), onOpenSettings)
                        // Listening again cannot fix a missing hub or a gateway rule.
                        ErrorKind.NO_HUB, ErrorKind.GATEWAY_REFUSED -> Unit
                        else -> VoiceRow(stringResource(R.string.voice_retry), onListenAgain)
                    }
                    VoiceRow(stringResource(R.string.voice_cancel), onCancel)
                }
            }
        }
    }
}

private fun errorRes(kind: ErrorKind): Int = when (kind) {
    ErrorKind.NO_HUB -> R.string.voice_no_hub
    ErrorKind.HUB_UNREACHABLE -> R.string.voice_hub_unreachable
    ErrorKind.GATEWAY_REFUSED -> R.string.voice_gateway_refused
    ErrorKind.HUB_FAILED -> R.string.voice_hub_failed
    ErrorKind.SPEECH -> R.string.voice_speech_error
    ErrorKind.PERMISSION -> R.string.voice_permission_denied
    ErrorKind.SILENCE -> R.string.voice_silence
    ErrorKind.DISABLED -> R.string.voice_disabled
}
