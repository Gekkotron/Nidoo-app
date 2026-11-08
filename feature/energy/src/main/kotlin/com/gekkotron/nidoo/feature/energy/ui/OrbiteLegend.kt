package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.energy.R
import com.gekkotron.nidoo.feature.energy.presentation.EnergyFormat
import com.gekkotron.nidoo.feature.energy.presentation.OrbiteArc
import com.gekkotron.nidoo.feature.energy.presentation.Sankey
import com.gekkotron.nidoo.feature.energy.presentation.SankeyRole
import java.util.Locale

/**
 * The rows under the Orbite rings: "Coming in" (inner ring) then "Going out" (outer ring), each arc
 * with its colour, name, what it does, watts and share. A phone cannot hover the rings, so every arc
 * is named here (the hub's `OrbiteRow`). A row opens what its arc opens.
 */
@Composable
internal fun OrbiteLegend(
    sankey: Sankey,
    inner: List<OrbiteArc>,
    outer: List<OrbiteArc>,
    locale: Locale,
    canOpen: (String) -> Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("energy-orbite-legend"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrbiteSection(stringResource(R.string.energy_orbite_in), inner, sankey, locale, canOpen, onTap)
        OrbiteSection(stringResource(R.string.energy_orbite_out), outer, sankey, locale, canOpen, onTap)
    }
}

@Composable
private fun OrbiteSection(
    title: String,
    arcs: List<OrbiteArc>,
    sankey: Sankey,
    locale: Locale,
    canOpen: (String) -> Boolean,
    onTap: (String) -> Unit,
) {
    if (arcs.isEmpty()) return
    Column {
        Text(
            title,
            style = NidooTheme.type.label,
            color = NidooTheme.colors.contentMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).semantics { heading() },
        )
        arcs.forEach { arc ->
            val node = sankey.nodes.firstOrNull { it.id == arc.id } ?: return@forEach
            OrbiteRow(arc, sankeyName(node), locale, canOpen(arc.id)) { onTap(arc.id) }
        }
    }
}

@Composable
private fun OrbiteRow(arc: OrbiteArc, name: String, locale: Locale, clickable: Boolean, onClick: () -> Unit) {
    val colors = NidooTheme.colors
    val note = orbiteNote(arc.role)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NidooDimens.minTouch)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("energy-orbite-row-${arc.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(sankeyColor(arc.role, arc.slot, colors)))
        Column(Modifier.weight(1f)) {
            Text(name, style = NidooTheme.type.body, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = NidooTheme.type.cardName, color = colors.contentMuted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(EnergyFormat.power(arc.watts, locale), style = NidooTheme.type.label, color = colors.content, maxLines = 1)
            Text("${arc.share} %", style = NidooTheme.type.cardName, color = colors.contentMuted, maxLines = 1)
        }
    }
}

/** What an arc does besides carrying its name: "production", "↘ import", "↗ export"… (the hub's `note`). */
@Composable
private fun orbiteNote(role: SankeyRole): String? = when (role) {
    SankeyRole.SOLAR -> stringResource(R.string.energy_orbite_production)
    SankeyRole.GRID -> "↘ " + stringResource(R.string.energy_orbite_import)
    SankeyRole.EXPORT -> "↗ " + stringResource(R.string.energy_orbite_export)
    SankeyRole.BATTERY -> "↘ " + stringResource(R.string.energy_orbite_discharging)
    SankeyRole.BATTERY_CHARGE -> "↗ " + stringResource(R.string.energy_orbite_charging)
    else -> null
}
