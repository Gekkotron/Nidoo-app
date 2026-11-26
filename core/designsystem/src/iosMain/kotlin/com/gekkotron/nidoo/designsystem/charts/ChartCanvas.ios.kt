package com.gekkotron.nidoo.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import kotlin.math.max

private const val GridAlpha = 0.3f

/** Everything a chart draws besides its data: the plot rectangle and the text style the axes use. */
internal class ChartSurface(val measurer: TextMeasurer, val style: TextStyle, val muted: Color, val card: Color, val content: Color)

/**
 * The 220 dp Canvas both iOS charts share. [onTap] gets the tap's x in px and the canvas size, and returns the marker
 * index (or null); [draw] receives that index.
 */
@Composable
internal fun ChartCanvas(
    modifier: Modifier,
    contentDescription: String,
    resetKey: Any,
    pick: (x: Float, size: Size) -> Int?,
    draw: DrawScope.(surface: ChartSurface, marker: Int?) -> Unit,
) {
    val surface = ChartSurface(
        measurer = rememberTextMeasurer(),
        style = NidooTheme.type.label.copy(color = NidooTheme.colors.contentMuted),
        muted = NidooTheme.colors.contentMuted,
        card = NidooTheme.colors.card,
        content = NidooTheme.colors.content,
    )
    var marker by remember(resetKey) { mutableStateOf<Int?>(null) }
    Canvas(
        modifier.fillMaxWidth().height(ChartHeight)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(resetKey) { detectTapGestures { offset -> marker = pick(offset.x, size.toSize()) } },
    ) { draw(surface, marker) }
}

private fun androidx.compose.ui.unit.IntSize.toSize() = Size(width.toFloat(), height.toFloat())

/** Draws the y grid with its labels and the x labels; returns the rectangle left for the data. */
internal fun DrawScope.drawAxes(
    surface: ChartSurface,
    yTicks: List<Double>,
    scale: ValueScale,
    formatValue: (Double) -> String,
    xLabels: List<Pair<String, Float>>, // label and its centre as a 0..1 share of the plot width
    xInset: Float = 0f,
): Rect {
    val yLabels = yTicks.map { surface.measurer.measure(formatValue(it), surface.style) }
    val xMeasured = xLabels.map { surface.measurer.measure(it.first, surface.style) }
    val gap = 6.dp.toPx()
    val left = (yLabels.maxOfOrNull { it.size.width } ?: 0) + gap
    val bottomLabel = (xMeasured.maxOfOrNull { it.size.height } ?: 0)
    val plot = Rect(left, 4.dp.toPx(), size.width - 4.dp.toPx(), size.height - bottomLabel - gap)
    val grid = surface.muted.copy(alpha = GridAlpha)
    yTicks.forEachIndexed { i, tick ->
        val y = plot.bottom - scale.fraction(tick) * plot.height
        drawLine(grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val label = yLabels[i]
        drawText(label, topLeft = Offset(plot.left - gap - label.size.width, y - label.size.height / 2f))
    }
    drawLine(grid, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
    val usable = plot.width - 2 * xInset
    xLabels.forEachIndexed { i, (_, share) ->
        val label = xMeasured[i]
        val centre = plot.left + xInset + share * usable
        val x = (centre - label.size.width / 2f).coerceIn(plot.left, max(plot.left, plot.right - label.size.width))
        drawText(label, topLeft = Offset(x, plot.bottom + gap))
    }
    return plot
}

/** A vertical rule at [x] and the tooltip text near the top of the plot, kept inside it. */
internal fun DrawScope.drawMarker(surface: ChartSurface, plot: Rect, x: Float, text: String) {
    drawLine(surface.muted, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
    val label = surface.measurer.measure(text, surface.style.copy(color = surface.content))
    val pad = 6.dp.toPx()
    val boxWidth = label.size.width + 2 * pad
    val boxLeft = (x - boxWidth / 2f).coerceIn(plot.left, max(plot.left, plot.right - boxWidth))
    drawRoundRect(
        surface.card, Offset(boxLeft, plot.top), Size(boxWidth, label.size.height + 2 * pad), CornerRadius(6.dp.toPx()),
    )
    drawText(label, topLeft = Offset(boxLeft + pad, plot.top + pad))
}
