package com.gekkotron.nidoo.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.rounded
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/** One history sample: `t` is unix seconds. */
data class ChartPoint(val t: Long, val mean: Double, val min: Double, val max: Double)

/** One daily bar: `t` is unix seconds. */
data class ChartBar(val t: Long, val value: Double)

private val ChartHeight = 220.dp
private const val AxisLabelCount = 4
private const val BandAlpha = 0.15f
private const val BandKeyAlpha = 0.3f

@Composable
fun HistoryLineChart(
    points: List<ChartPoint>,
    color: Color,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** True draws just the mean with square steps (binary sensors). */
    step: Boolean = false,
    /**
     * Non-null shades the min/max band behind the mean and adds this line ("min 3,0 °C · max 21,5 °C")
     * to the tooltip; null draws the mean alone. Ignored with [step].
     */
    formatBand: ((min: Double, max: Double) -> String)? = null,
) {
    val band = formatBand?.takeUnless { step }
    val frame = modifier.fillMaxWidth().height(ChartHeight)
        .semantics { this.contentDescription = contentDescription }
    if (points.isEmpty()) {
        Box(frame)
        return
    }
    val first = points.first().t
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, band != null) {
        val xs = points.map { (it.t - first).toDouble() }
        producer.runTransaction {
            lineSeries {
                // Min and max ride along as hidden lines: they set the y-range, feed the tooltip and outline the band.
                if (band != null) {
                    series(xs, points.map { it.min })
                    series(xs, points.map { it.max })
                }
                series(xs, points.map { it.mean })
            }
        }
    }
    val hidden = LineCartesianLayer.rememberLineFor(Color.Transparent, 1f)
    val meanLine = LineCartesianLayer.rememberLineFor(color, 2f, if (step) StepPointConnector else null)
    val xUnit = remember(points) { xStep(points.map { it.t - first }) }
    val spacing = remember(points) {
        val span = points.last().t - first
        maxOf(1, (span / xUnit / AxisLabelCount).toInt())
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = NidooTheme.colors.content, lineCount = if (band != null) 2 else 1),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val at = (targets.firstOrNull() as? LineCartesianLayerMarkerTarget)?.points.orEmpty()
            val point = at.lastOrNull()
            if (point == null) "" else {
                val t = first + point.entry.x.toLong()
                val line = "${formatTime(t)}  ${formatValue(point.entry.y)}"
                val (low, high) = at.getOrNull(0)?.entry?.y to at.getOrNull(1)?.entry?.y
                if (band != null && at.size == 3 && low != null && high != null && low < high) "$line\n${band(low, high)}" else line
            }
        },
    )
    val bandFill = color.copy(alpha = BandAlpha).toArgb()
    val bandDecoration = remember(bandFill) { MinMaxBand(bandFill) }
    val axisLine = NidooTheme.colors.contentMuted.copy(alpha = 0.3f)
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = if (band != null) LineCartesianLayer.LineProvider.series(hidden, hidden, meanLine)
            else LineCartesianLayer.LineProvider.series(meanLine),
        ),
        startAxis = VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = NidooTheme.colors.contentMuted),
            line = rememberAxisLineComponent(fill = fill(axisLine)),
            tick = rememberAxisTickComponent(fill = fill(axisLine)),
            guideline = rememberAxisGuidelineComponent(fill = fill(axisLine)),
            valueFormatter = CartesianValueFormatter { _, value, _ -> formatValue(value) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = NidooTheme.colors.contentMuted),
            line = rememberAxisLineComponent(fill = fill(axisLine)),
            tick = rememberAxisTickComponent(fill = fill(axisLine)),
            guideline = rememberAxisGuidelineComponent(fill = fill(axisLine)),
            valueFormatter = CartesianValueFormatter { _, value, _ -> formatTime(first + value.toLong()) },
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }),
        ),
        marker = marker,
        decorations = listOfNotNull(bandDecoration.takeIf { band != null }),
    )
    // Keyed on the data: a new range or mode starts again from the whole span.
    key(points) {
        CartesianChartHost(
            chart = chart,
            modelProducer = producer,
            modifier = frame,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            zoomState = rememberHistoryZoom(points.last().t - first, xUnit),
        )
    }
}

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

/**
 * Shades between the line layer's first two series (min, max) under the lines. Vico 2.1 only fills
 * a line down to a constant, so this maps the entries to pixels the way `LineCartesianLayer` does.
 */
private class MinMaxBand(color: Int) : Decoration {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private val path = Path()

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        val series = context.model.models.filterIsInstance<LineCartesianLayerModel>().firstOrNull()?.series ?: return
        if (series.size < 3) return
        val (lows, highs) = series[0] to series[1]
        if (lows.size < 2 || lows.size != highs.size) return
        with(context) {
            val bounds = layerBounds
            val dir = layoutDirectionMultiplier
            val x0 = (if (isLtr) bounds.left else bounds.right) + dir * layerDimensions.startPadding - scroll
            val yRange = ranges.getYRange(null)
            fun x(v: Double) = x0 + dir * layerDimensions.xSpacing * ((v - ranges.minX) / ranges.xStep).toFloat()
            fun y(v: Double) = bounds.bottom - ((v - yRange.minY) / yRange.length).toFloat() * bounds.height()
            path.rewind()
            highs.forEachIndexed { i, e -> if (i == 0) path.moveTo(x(e.x), y(e.y)) else path.lineTo(x(e.x), y(e.y)) }
            for (i in lows.indices.reversed()) path.lineTo(x(lows[i].x), y(lows[i].y))
            path.close()
            val saved = canvas.save()
            canvas.clipRect(bounds)
            canvas.drawPath(path, paint)
            canvas.restoreToCount(saved)
        }
    }
}

@Composable
fun DailyBarsChart(
    bars: List<ChartBar>,
    /** Drawn as a line across the bars; null draws none. */
    average: Double?,
    color: Color,
    formatValue: (Double) -> String,
    formatDay: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val frame = modifier.fillMaxWidth().height(ChartHeight)
        .semantics { this.contentDescription = contentDescription }
    if (bars.isEmpty()) {
        Box(frame)
        return
    }
    val first = bars.first().t
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(bars) {
        val xs = bars.map { (it.t - first).toDouble() }
        producer.runTransaction { columnSeries { series(xs, bars.map { it.value }) } }
    }
    val step = remember(bars) { xStep(bars.map { it.t - first }) }
    val spacing = remember(bars) {
        maxOf(1, ((bars.last().t - first) / step / AxisLabelCount).toInt())
    }
    val axisLine = NidooTheme.colors.contentMuted.copy(alpha = 0.3f)
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(
                    fill = fill(color),
                    thickness = 8.dp,
                    shape = CorneredShape.rounded(2.dp),
                ),
            ),
        ),
        startAxis = VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = NidooTheme.colors.contentMuted),
            line = rememberAxisLineComponent(fill = fill(axisLine)),
            tick = rememberAxisTickComponent(fill = fill(axisLine)),
            guideline = rememberAxisGuidelineComponent(fill = fill(axisLine)),
            valueFormatter = CartesianValueFormatter { _, value, _ -> formatValue(value) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = NidooTheme.colors.contentMuted),
            line = rememberAxisLineComponent(fill = fill(axisLine)),
            tick = rememberAxisTickComponent(fill = fill(axisLine)),
            guideline = rememberAxisGuidelineComponent(fill = fill(axisLine)),
            valueFormatter = CartesianValueFormatter { _, value, _ -> formatDay(first + value.toLong()) },
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }),
        ),
        decorations = listOfNotNull(average?.let { rememberAverageLine(it) }),
    )
    key(bars) {
        CartesianChartHost(
            chart = chart,
            modelProducer = producer,
            modifier = frame,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            zoomState = rememberHistoryZoom(bars.last().t - first, step),
        )
    }
}

/** A solid rule at [average]: solid so it reads apart from the dashed guidelines. */
@Composable
private fun rememberAverageLine(average: Double): HorizontalLine {
    val line = rememberLineComponent(
        fill = fill(NidooTheme.colors.content.copy(alpha = 0.85f)),
        thickness = 1.5.dp,
    )
    return remember(average, line) { HorizontalLine(y = { average }, line = line, labelComponent = null) }
}

@Composable
fun ChartSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(ChartHeight)
            .background(NidooTheme.colors.card, RoundedCornerShape(12.dp)),
    )
}

/** Pinch-zoom and pan: opens on the whole span, never zooms out past it, nor in past [maxZoomSpan]. */
@Composable
private fun rememberHistoryZoom(span: Long, step: Long) = rememberVicoZoomState(
    zoomEnabled = true,
    initialZoom = Zoom.Content,
    minZoom = Zoom.Content,
    maxZoom = Zoom.max(Zoom.x(maxZoomSpan(span, step)), Zoom.Content),
)

/** The narrowest x-span (seconds) zooming may show: a tenth of the data, but never fewer than four samples. */
internal fun maxZoomSpan(span: Long, step: Long): Double = maxOf(span / 10.0, 4.0 * step)

@Composable
private fun LineCartesianLayer.Companion.rememberLineFor(
    color: Color,
    thicknessDp: Float,
    pointConnector: LineCartesianLayer.PointConnector? = null,
) = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(fill(color)),
    stroke = LineCartesianLayer.LineStroke.continuous(thickness = thicknessDp.dp),
    pointConnector = pointConnector ?: LineCartesianLayer.PointConnector.Sharp,
)

/** Draws right-angle steps: hold the previous Y across the x-span, then jump to the new Y at the next sample. */
object StepPointConnector : LineCartesianLayer.PointConnector {
    override fun connect(context: CartesianDrawingContext, path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        path.lineTo(x2, y1)
        path.lineTo(x2, y2)
    }
}

/** Vico spaces x in multiples of the gcd of the deltas; labels are placed in those units. */
private fun xStep(offsets: List<Long>): Long {
    var g = 0L
    for (i in 1 until offsets.size) g = gcd(g, offsets[i] - offsets[i - 1])
    return if (g <= 0L) 1L else g
}

private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
