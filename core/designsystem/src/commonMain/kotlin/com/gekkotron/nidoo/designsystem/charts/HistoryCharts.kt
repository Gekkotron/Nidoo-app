package com.gekkotron.nidoo.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme

/** One history sample: `t` is unix seconds. */
data class ChartPoint(val t: Long, val mean: Double, val min: Double, val max: Double)

/** One daily bar: `t` is unix seconds. */
data class ChartBar(val t: Long, val value: Double)

val ChartHeight = 220.dp
private const val BandKeyAlpha = 0.3f

/**
 * The mean over time. Android draws a Vico chart (zoom, pan, tooltip); iOS draws a Canvas with the same axes and a
 * tap marker.
 *
 * [step] true draws just the mean with square steps (binary sensors). A non-null [formatBand] shades the min/max
 * band behind the mean and adds this line ("min 3,0 °C · max 21,5 °C") to the tooltip; null draws the mean alone.
 * It is ignored with [step].
 */
@Composable
expect fun HistoryLineChart(
    points: List<ChartPoint>,
    color: Color,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    step: Boolean = false,
    formatBand: ((min: Double, max: Double) -> String)? = null,
)

/** Daily bars; [average] is drawn as a line across them, null draws none. */
@Composable
expect fun DailyBarsChart(
    bars: List<ChartBar>,
    average: Double?,
    color: Color,
    formatValue: (Double) -> String,
    formatDay: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
)

/** The key under a banded chart: the line is the average, the shaded band the min–max range (as on the hub). */
@Composable
fun HistoryBandKey(color: Color, averageLabel: String, bandLabel: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyItem(Modifier.size(16.dp, 2.dp).background(color, RoundedCornerShape(1.dp)), averageLabel)
        KeyItem(Modifier.size(12.dp, 8.dp).background(color.copy(alpha = BandKeyAlpha), RoundedCornerShape(2.dp)), bandLabel)
    }
}

@Composable
private fun KeyItem(swatch: Modifier, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(swatch)
        Text(label, style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted)
    }
}

@Composable
fun ChartSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(ChartHeight)
            .background(NidooTheme.colors.card, RoundedCornerShape(12.dp)),
    )
}
