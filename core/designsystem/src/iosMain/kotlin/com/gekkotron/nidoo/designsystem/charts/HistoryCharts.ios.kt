package com.gekkotron.nidoo.designsystem.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val BandAlpha = 0.15f
private const val AverageAlpha = 0.85f
private const val AxisLabelCount = 4

@Composable
actual fun HistoryLineChart(
    points: List<ChartPoint>,
    color: Color,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    contentDescription: String,
    modifier: Modifier,
    step: Boolean,
    formatBand: ((min: Double, max: Double) -> String)?,
) {
    val band = formatBand?.takeUnless { step }
    val times = remember(points) { points.map { it.t } }
    val scale = remember(points, band != null) {
        valueScale(points.flatMap { if (band != null) listOf(it.min, it.max) else listOf(it.mean) })
    }
    val first = points.firstOrNull()?.t ?: 0L
    val span = max(1L, (points.lastOrNull()?.t ?: 0L) - first).toFloat()
    ChartCanvas(
        modifier = modifier,
        contentDescription = contentDescription,
        resetKey = points,
        pick = { share -> if (canDrawLine(points.size)) nearestIndex(times, first + share * span.toDouble()) else null },
    ) { surface, marker, holder ->
        holder.rect = null
        if (!canDrawLine(points.size)) return@ChartCanvas // just the frame: no data, no labels
        val xLabels = timeTicks(first, points.last().t, AxisLabelCount)
            .mapIndexed { i, t -> formatTime(t) to i / (AxisLabelCount - 1f) }
        val plot = drawAxes(surface, axisTicks(scale), scale, formatValue, xLabels, holder)
        fun x(t: Long) = plot.left + (t - first) / span * plot.width
        fun y(v: Double) = plot.bottom - scale.fraction(v) * plot.height
        if (band != null) {
            val area = Path().apply {
                moveTo(x(points.first().t), y(points.first().max))
                points.forEach { lineTo(x(it.t), y(it.max)) }
                points.asReversed().forEach { lineTo(x(it.t), y(it.min)) }
                close()
            }
            drawPath(area, color.copy(alpha = BandAlpha))
        }
        val line = Path().apply {
            moveTo(x(points.first().t), y(points.first().mean))
            for (i in 1 until points.size) {
                if (step) lineTo(x(points[i].t), y(points[i - 1].mean))
                lineTo(x(points[i].t), y(points[i].mean))
            }
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        marker?.let { i ->
            val p = points[i]
            val head = "${formatValue(p.mean)} · ${formatTime(p.t)}"
            val text = if (band != null && p.min < p.max) "$head\n${band(p.min, p.max)}" else head
            drawMarker(surface, plot, x(p.t), text)
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(p.t), y(p.mean)))
        }
    }
}

@Composable
actual fun DailyBarsChart(
    bars: List<ChartBar>,
    average: Double?,
    color: Color,
    formatValue: (Double) -> String,
    formatDay: (Long) -> String,
    contentDescription: String,
    modifier: Modifier,
) {
    // Bars rest on zero, so the scale is not the line chart's: zero at the bottom, 5% headroom above the tallest.
    val scale = remember(bars) {
        val top = bars.maxOfOrNull { it.value } ?: 0.0
        ValueScale(min(0.0, bars.minOfOrNull { it.value } ?: 0.0), if (top > 0.0) top * 1.05 else 1.0)
    }
    ChartCanvas(
        modifier = modifier,
        contentDescription = contentDescription,
        resetKey = bars,
        pick = { share -> if (bars.isEmpty()) null else (share * bars.size).toInt().coerceIn(0, bars.size - 1) },
    ) { surface, marker, holder ->
        holder.rect = null
        if (bars.isEmpty()) return@ChartCanvas // just the frame: no data, no labels
        val last = bars.size - 1
        val xLabels = List(min(AxisLabelCount, bars.size)) { i ->
            val index = if (bars.size == 1) 0 else (last * i / (min(AxisLabelCount, bars.size) - 1f)).toInt()
            formatDay(bars[index].t) to (index + 0.5f) / bars.size
        }
        val plot = drawAxes(surface, axisTicks(scale), scale, formatValue, xLabels, holder)
        val slot = plot.width / bars.size
        val barWidth = min(8.dp.toPx(), slot * 0.7f)
        fun centre(i: Int) = plot.left + slot * (i + 0.5f)
        fun y(v: Double) = plot.bottom - scale.fraction(v) * plot.height
        val zero = y(0.0)
        bars.forEachIndexed { i, bar ->
            val top = y(bar.value)
            drawRoundRect(
                color,
                Offset(centre(i) - barWidth / 2f, min(top, zero)),
                Size(barWidth, abs(zero - top).coerceAtLeast(1f)),
                CornerRadius(2.dp.toPx()),
            )
        }
        average?.let {
            drawLine(
                surface.content.copy(alpha = AverageAlpha),
                Offset(plot.left, y(it)), Offset(plot.right, y(it)),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        marker?.let { i -> drawMarker(surface, plot, centre(i), "${formatValue(bars[i].value)} · ${formatDay(bars[i].t)}") }
    }
}
