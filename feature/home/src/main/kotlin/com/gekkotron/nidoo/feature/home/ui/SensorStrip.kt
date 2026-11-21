package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.TapAction
import com.gekkotron.nidoo.model.Kind

private val LONE_CLASSES = setOf("temperature", "humidity")

/**
 * A room's read-only sensors as compact pills (rooms design §6.4): they tell, they don't control, so
 * they don't each take a tile. Tap opens the history when there is one; long-press customizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SensorStrip(cards: List<CardUi>, onClick: (CardUi) -> Unit, onLongClick: (CardUi) -> Unit, modifier: Modifier = Modifier) {
    // The room's only thermometer needs no name: "21.5 °C" says it. Two of them do.
    val lone = cards.filter { it.kind == Kind.SENSOR && it.deviceClass in LONE_CLASSES }
        .groupBy { it.deviceClass }
        .filterValues { it.size == 1 }
        .values.mapTo(mutableSetOf()) { it.single().id }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEach { card -> SensorPill(card, showName = card.id !in lone, onClick = { onClick(card) }, onLongClick = { onLongClick(card) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SensorPill(card: CardUi, showName: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = NidooTheme.colors
    val haptics = LocalHapticFeedback.current
    val value = if (card.available) cardValueText(card.value, card.kind) else stringResource(R.string.value_unavailable)
    val room = card.areaName ?: stringResource(R.string.area_unassigned)
    val description = stringResource(R.string.strip_pill_description, card.name, value, room)
    val customize = stringResource(R.string.action_customize)
    val longClick by rememberUpdatedState(onLongClick)
    val gestures = if (card.tap != TapAction.NONE) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onLongClickLabel = customize,
        )
    } else {
        // Long-press only: no click action, or TalkBack would announce a double-tap that does nothing.
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                })
            }
            .semantics { onLongClick(label = customize) { longClick(); true } }
    }
    Box(
        Modifier
            .heightIn(min = NidooDimens.minTouch)
            .testTag("strip-${card.id}")
            .alpha(if (card.stale || !card.available) 0.6f else 1f)
            .then(gestures)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(CircleShape).border(1.dp, c.chipBorder.takeIf { it.alpha > 0f } ?: c.cardBorder, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KindIcon(iconSpec(card), Modifier.size(16.dp))
            if (showName) {
                Text(card.name, style = NidooTheme.type.label, color = LocalContentColor.current.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = NidooTheme.type.label, maxLines = 1)
        }
    }
}
