package com.gekkotron.nidoo.feature.security.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.designsystem.icons.DoorIcon
import com.gekkotron.nidoo.designsystem.icons.GarageIcon
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.designsystem.icons.WindowIcon
import com.gekkotron.nidoo.feature.security.R
import com.gekkotron.nidoo.feature.security.presentation.GroupCount
import com.gekkotron.nidoo.feature.security.presentation.SensorRow
import com.gekkotron.nidoo.feature.security.presentation.SinceFormat
import com.gekkotron.nidoo.feature.security.presentation.SinceSpan
import com.gekkotron.nidoo.model.SensorGroup
import java.time.Instant

/** A half-width tile: icon and name, then room and state. Long names and rooms are cut, never the state. */
@Composable
internal fun SensorTile(row: SensorRow, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = activeColor(row)
    val staleText = stringResource(R.string.security_stale)
    NidooCard(
        modifier
            .testTag("sensor-${row.id}")
            .alpha(if (!row.available) 0.55f else if (row.stale) 0.6f else 1f)
            .then(if (row.stale) Modifier.semantics { stateDescription = staleText } else Modifier),
        accent = row.available && row.active == true,
        accentColor = if (row.group.danger) NidooTheme.colors.heat else null,
        onClick = onClick,
        minHeight = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SensorIcon(row, accent)
            Text(displayName(row), style = NidooTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.areaName ?: stringResource(R.string.security_unassigned),
                style = NidooTheme.type.label,
                color = NidooTheme.colors.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val word = stateWord(row)
            val span = sinceSpan(row, now)
            if (span == null) {
                Text(word, style = NidooTheme.type.body, color = accent ?: NidooTheme.colors.content, maxLines = 1)
            } else {
                // TalkBack reads "Closed for 2 hours", not "Closed dot 2 h".
                val spoken = stringResource(R.string.security_state_for, word, sinceLong(span))
                Text(
                    stringResource(R.string.security_state_since, word, sinceShort(span)),
                    style = NidooTheme.type.body,
                    color = accent ?: NidooTheme.colors.content,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
    }
}

/** Rose for danger, amber for intrusion; null when calm, unknown or unavailable (security spec §4.3). */
@Composable
private fun activeColor(row: SensorRow): Color? {
    if (!row.available || row.active != true) return null
    return if (row.group.danger) NidooTheme.colors.heat else NidooTheme.colors.warning
}

@Composable
private fun SensorIcon(row: SensorRow, accent: Color?) {
    val open = if (row.active == true) 1f else 0f
    val m = Modifier.size(22.dp)
    when (row.deviceClass) {
        "window" -> WindowIcon(open, accent, m)
        "garage_door" -> GarageIcon(open, accent, m)
        "door", "opening" -> DoorIcon(open, accent, m)
        else -> StaticIcon(glyph(row.group), accent, m)
    }
}

private fun glyph(group: SensorGroup): NidooGlyph = when (group) {
    SensorGroup.MOTION -> NidooGlyph.WALKER
    SensorGroup.SMOKE, SensorGroup.CO, SensorGroup.GAS -> NidooGlyph.FLAME
    SensorGroup.LEAK -> NidooGlyph.DROP
    SensorGroup.SOS -> NidooGlyph.SHIELD
    SensorGroup.OPENING, SensorGroup.VIBRATION, SensorGroup.TAMPER -> NidooGlyph.DOT
}

/** Null when there is nothing trustworthy to show: no time, unavailable, cached (stale) or no state (last-change spec §4.5). */
internal fun sinceSpan(row: SensorRow, now: Instant): SinceSpan? {
    val since = row.since ?: return null
    if (!row.available || row.stale || row.active == null) return null
    return SinceFormat.span(since, now)
}

/** Fall back to the group label when the entity name is the raw HA device_class (e.g. "smoke", "water_leak"). */
@Composable
internal fun displayName(row: SensorRow): String {
    if (!isRawClassName(row)) return row.name
    return stringResource(
        when (row.group) {
            SensorGroup.OPENING -> R.string.security_label_opening
            SensorGroup.MOTION -> R.string.security_label_motion
            SensorGroup.SMOKE -> R.string.security_label_smoke
            SensorGroup.CO -> R.string.security_label_co
            SensorGroup.GAS -> R.string.security_label_gas
            SensorGroup.LEAK -> R.string.security_label_leak
            SensorGroup.SOS -> R.string.security_label_sos
            SensorGroup.VIBRATION -> R.string.security_label_vibration
            SensorGroup.TAMPER -> R.string.security_label_tamper
        },
    )
}

/** HA discovery sometimes names binary_sensors by their device_class alone; those names are not human labels. */
internal fun isRawClassName(row: SensorRow): Boolean {
    val name = row.name.trim().lowercase()
    if (name.isEmpty()) return true
    if (row.deviceClass?.lowercase() == name) return true
    return name in RAW_CLASS_NAMES
}

private val RAW_CLASS_NAMES = setOf(
    "smoke",
    "monoxide", "carbon_monoxide", "co",
    "gas",
    "moisture", "water_leak", "leak",
    "motion", "occupancy", "presence",
    "opening", "door", "window", "garage_door",
    "vibration",
    "tamper",
    "sos", "safety", "sound", "problem",
)

@Composable
internal fun stateWord(row: SensorRow): String {
    if (!row.available) return stringResource(R.string.security_unavailable)
    val active = row.active ?: return stringResource(R.string.security_unknown)
    return stringResource(
        when (row.group) {
            SensorGroup.OPENING -> if (active) R.string.security_open else R.string.security_closed
            SensorGroup.MOTION -> if (active) R.string.security_motion else R.string.security_clear
            SensorGroup.SMOKE, SensorGroup.CO, SensorGroup.GAS -> if (active) R.string.security_detected else R.string.security_clear
            SensorGroup.LEAK -> if (active) R.string.security_leak else R.string.security_dry
            SensorGroup.SOS -> if (active) R.string.security_sos else R.string.security_ok
            SensorGroup.VIBRATION -> if (active) R.string.security_vibration else R.string.security_still
            SensorGroup.TAMPER -> if (active) R.string.security_tampered else R.string.security_ok
        },
    )
}

/** "2 open · 1 motion", or "All clear". */
@Composable
internal fun summaryText(summary: List<GroupCount>): String {
    if (summary.isEmpty()) return stringResource(R.string.security_all_clear)
    return summary.map { (group, n) ->
        pluralStringResource(
            when (group) {
                SensorGroup.OPENING -> R.plurals.security_count_opening
                SensorGroup.MOTION -> R.plurals.security_count_motion
                SensorGroup.SMOKE -> R.plurals.security_count_smoke
                SensorGroup.CO -> R.plurals.security_count_co
                SensorGroup.GAS -> R.plurals.security_count_gas
                SensorGroup.LEAK -> R.plurals.security_count_leak
                SensorGroup.SOS -> R.plurals.security_count_sos
                SensorGroup.VIBRATION -> R.plurals.security_count_vibration
                SensorGroup.TAMPER -> R.plurals.security_count_tamper
            },
            n, n,
        )
    }.joinToString(" · ")
}
