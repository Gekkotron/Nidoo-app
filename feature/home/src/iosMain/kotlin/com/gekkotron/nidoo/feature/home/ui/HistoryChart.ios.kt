package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.history.HistoryPoint

private const val BAND_ALPHA = 0.15f

/** The mean as a line over its min/max band; no axes or tooltip (the tile's title says what it shows). */
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
    val frame = modifier.fillMaxWidth().height(120.dp).semantics { this.contentDescription = contentDescription }
    if (points.size < 2) {
        Canvas(frame) {}
        return
    }
    val band = formatBand != null
    Canvas(frame) {
        val first = points.first().t
        val span = (points.last().t - first).coerceAtLeast(1).toFloat()
        val low = points.minOf { if (band) it.min else it.mean }
        val high = points.maxOf { if (band) it.max else it.mean }
        val range = (high - low).coerceAtLeast(1e-9).toFloat()
        fun x(p: HistoryPoint) = (p.t - first) / span * size.width
        fun y(v: Double) = size.height - ((v - low).toFloat() / range) * size.height
        if (band) {
            val area = Path().apply {
                moveTo(x(points.first()), y(points.first().max))
                points.forEach { lineTo(x(it), y(it.max)) }
                points.asReversed().forEach { lineTo(x(it), y(it.min)) }
                close()
            }
            drawPath(area, color.copy(alpha = BAND_ALPHA))
        }
        val line = Path().apply {
            moveTo(x(points.first()), y(points.first().mean))
            points.drop(1).forEach { lineTo(x(it), y(it.mean)) }
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(points.last()), y(points.last().mean)))
    }
}
