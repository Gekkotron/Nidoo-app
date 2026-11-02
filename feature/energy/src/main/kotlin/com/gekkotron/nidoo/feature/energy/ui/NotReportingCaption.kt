package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.energy.R
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode

/** "Not reporting: Grid, AC" under Flux and Orbite, so a silent sensor never just vanishes (spec). Hidden when all report. */
@Composable
internal fun NotReportingCaption(nodes: List<EnergyNode>, modifier: Modifier = Modifier) {
    if (nodes.isEmpty()) return
    val names = nodes.map { nodeLabel(it) }.joinToString(", ")
    Text(
        stringResource(R.string.energy_not_reporting, names),
        style = NidooTheme.type.label,
        color = NidooTheme.colors.contentMuted,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
