package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.designsystem.charts.ChartPoint
import com.gekkotron.nidoo.designsystem.charts.HistoryLineChart
import com.gekkotron.nidoo.history.HistoryPoint

@Composable
internal actual fun HistoryChart(
    points: List<HistoryPoint>,
    color: Color,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    contentDescription: String,
    modifier: Modifier,
    formatBand: ((min: Double, max: Double) -> String)?,
) {
    val chartPoints = remember(points) { points.map { ChartPoint(it.t, it.mean, it.min, it.max) } }
    HistoryLineChart(
        points = chartPoints,
        color = color,
        formatValue = formatValue,
        formatTime = formatTime,
        contentDescription = contentDescription,
        modifier = modifier,
        formatBand = formatBand,
    )
}
