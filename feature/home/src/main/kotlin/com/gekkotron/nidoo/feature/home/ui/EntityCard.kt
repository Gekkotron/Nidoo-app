package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.designsystem.components.PendingDot
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.feature.home.presentation.TapAction
import com.gekkotron.nidoo.feature.home.presentation.parseTint
import com.gekkotron.nidoo.model.Command

@Composable
internal fun EntityCard(
    card: CardUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onForce: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The card's own buttons (a media player's play/pause); the tap still opens the sheet. */
    onSend: (Command) -> Unit = {},
) {
    val valueText = if (card.available) cardValueText(card.value, card.kind) else stringResource(R.string.value_unavailable)
    val room = card.areaName ?: stringResource(R.string.area_unassigned)
    val heater = card.heater
    val heaterExtras = heater?.takeIf { card.available }?.let { h ->
        listOfNotNull(h.chip?.let { heaterChipText(it) }, h.inlet?.let { inletText(card, it) }).joinToString(", ")
    }.orEmpty()
    val description = if (heaterExtras.isNotEmpty() && !card.hidden) {
        stringResource(R.string.card_description_water_heater, card.name, valueText, heaterExtras, room)
    } else {
        stringResource(if (card.hidden) R.string.card_description_hidden else R.string.card_description, card.name, valueText, room)
    }
    val onText = stringResource(R.string.value_on)
    val offText = stringResource(R.string.value_off)
    val staleText = stringResource(R.string.card_stale)
    val toggle = card.tap == TapAction.TOGGLE
    val icon = iconSpec(card)
    val media = (card.value as? CardValue.MediaPlayer)?.takeIf { card.available }
    // Idle has no cover, so no colour either: the plain card.
    val tint = media?.takeIf { it.state != "idle" }?.let { parseTint(it.color) }
    NidooCard(
        modifier = modifier
            .testTag("card-${card.id}")
            .alpha(if (card.hidden || card.stale) 0.6f else 1f)
            .semantics {
                contentDescription = description
                if (toggle) stateDescription = if (card.on) onText else offText
                if (card.stale) stateDescription = staleText
            },
        on = card.on,
        accent = icon.openContact,
        enabled = card.available,
        onClick = if (card.tap != TapAction.NONE) onClick else null,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(if (card.hasSheet) R.string.action_controls else R.string.action_customize),
        tint = tint,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                heater != null -> TankGlyph(heating = heater.heating)
                media != null -> MediaCardCover(media)
                else -> KindIcon(icon)
            }
            Spacer(Modifier.weight(1f))
            if (media != null) MediaCardButton(card, media, onSend)
            card.forceToggle?.let { forced ->
                val forceDescription = stringResource(R.string.card_force_toggle, card.name)
                Switch(
                    checked = forced,
                    onCheckedChange = { onForce() },
                    enabled = card.available,
                    modifier = Modifier.semantics { contentDescription = forceDescription },
                )
            }
            if (card.pending) PendingDot()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            card.name,
            style = NidooTheme.type.cardName,
            color = LocalContentColor.current.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            heater != null && card.available -> WaterHeaterDetails(card, heater, valueText)
            media != null -> MediaPlayerDetails(media, valueText, tinted = tint != null)
            else -> Text(valueText, style = NidooTheme.type.cardValue)
        }
    }
}
