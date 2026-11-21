package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.RoomCardUi
import com.gekkotron.nidoo.feature.home.presentation.RoomCommands

/**
 * One room on the Rooms view (rooms design §6.2): icon, name, a one-line status, and a button per
 * kind the room has that acts on the whole room in one tap. The card itself opens the room.
 */
@Composable
internal fun RoomCard(room: RoomCardUi, onOpen: () -> Unit, onLights: () -> Unit, onShutters: () -> Unit, modifier: Modifier = Modifier) {
    val c = NidooTheme.colors
    val name = roomName(room)
    val status = roomStatus(room)
    val description = stringResource(R.string.room_card_description, name, status)
    NidooCard(
        modifier = modifier.testTag("room-${room.areaId}").semantics { contentDescription = description },
        on = room.lightsOn > 0,
        onClick = onOpen,
        minHeight = 72.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.chip.takeIf { it.alpha > 0f } ?: c.cardBorder),
                contentAlignment = Alignment.Center,
            ) {
                StaticIcon(roomGlyph(room.icon), modifier = Modifier.clearAndSetSemantics { })
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = NidooTheme.type.cardValue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status,
                    style = NidooTheme.type.label,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (room.buttons) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (room.lights.isNotEmpty()) {
                        val lit = room.lightsOn > 0
                        val state = stringResource(if (lit) R.string.value_on else R.string.value_off)
                        RoomButton(
                            tag = "room-${room.areaId}-lights",
                            label = lightsActionLabel(room),
                            state = state,
                            lit = lit,
                            enabled = room.lights.any { it.available },
                            onClick = onLights,
                        ) { KindIcon(IconSpec.Light(on = lit, color = null, available = true)) }
                    }
                    if (room.shutters.isNotEmpty()) {
                        val open = room.shuttersOpen > 0
                        RoomButton(
                            tag = "room-${room.areaId}-shutters",
                            label = shutterActionLabel(room),
                            state = null,
                            lit = false,
                            enabled = RoomCommands.shutterAction(room) != null,
                            onClick = onShutters,
                        ) { KindIcon(IconSpec.Shutter(target = if (open) 1f else 0f, moving = room.shuttersMoving)) }
                    }
                }
            }
        }
    }
}

/** A 36 dp round button in a 48 dp touch target. [label] says what the tap will do. */
@Composable
private fun RoomButton(tag: String, label: String, state: String?, lit: Boolean, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val c = NidooTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(NidooDimens.minTouch)
            .testTag(tag)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics {
                contentDescription = label
                if (state != null) stateDescription = state
            }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (lit) c.cardOnBorder else c.chip.takeIf { it.alpha > 0f } ?: c.cardBorder),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}
