package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.history.HistoryPoint

/**
 * A history line for a dashboard tile. Android draws the Vico chart (axes, marker, min/max band); iOS draws a plain
 * line with the same band until the shared History screen gets a multiplatform chart (Phase 5).
 */
@Composable
internal expect fun HistoryChart(
    points: List<HistoryPoint>,
    color: Color,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    contentDescription: String,
    modifier: Modifier,
    formatBand: ((min: Double, max: Double) -> String)?,
)
