package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooColors
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.energy.R
import com.gekkotron.nidoo.feature.energy.presentation.Anchor
import com.gekkotron.nidoo.feature.energy.presentation.EnergyFormat
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode
import com.gekkotron.nidoo.feature.energy.presentation.EnergySankey
import com.gekkotron.nidoo.feature.energy.presentation.EnergyUiState
import com.gekkotron.nidoo.feature.energy.presentation.Frame
import com.gekkotron.nidoo.feature.energy.presentation.Pt
import com.gekkotron.nidoo.feature.energy.presentation.SankeyBand
import com.gekkotron.nidoo.feature.energy.presentation.SankeyLayout
import com.gekkotron.nidoo.feature.energy.presentation.SankeyNode
import com.gekkotron.nidoo.feature.energy.presentation.SankeyRole
import com.gekkotron.nidoo.feature.energy.presentation.hit
import com.gekkotron.nidoo.feature.energy.presentation.notReporting
import com.gekkotron.nidoo.feature.energy.presentation.tapTarget
import java.util.Locale
import com.gekkotron.nidoo.designsystem.R as DsR

/** Phones get the hub's vertical frame; wider windows the horizontal one. */
private val WIDE_FROM = 600.dp

/** How far, in frame units, a bar's touch target reaches past it along the main axis. */
private const val BAR_SLOP = 10.0

/**
 * The Flux view: the live flow as a Sankey (spec "Energy views"). Static between readings: no frame
 * clock. Taps on a bar or band open its node; Others opens the consumers list.
 */
@Composable
internal fun FluxDiagram(state: EnergyUiState, locale: Locale, onMore: () -> Unit, onNode: (EnergyNode) -> Unit, modifier: Modifier = Modifier) {
    val sankey = remember(state) { EnergySankey.build(state) }
    val description = energyDescription(state, locale)
    val tap: (String) -> Unit = { id -> if (id == "others") onMore() else state.tapTarget(id)?.let(onNode) }
    Column(modifier.fillMaxWidth().testTag("energy-flux")) {
        if (!sankey.isEmpty) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val frame = if (maxWidth < WIDE_FROM) Frame.NARROW else Frame.WIDE
                val placed = remember(sankey, frame) { SankeyLayout.place(sankey, frame) }
                val byId = remember(sankey) { sankey.nodes.associateBy { it.id } }
                val unit: Dp = maxWidth / frame.w.toFloat()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio((frame.w / frame.h).toFloat())
                        .pointerInput(placed) {
                            val k = size.width / frame.w
                            detectTapGestures { o -> placed.hit(o.x / k, o.y / k, frame, 0.0)?.let(tap) }
                        },
                ) {
                    val colors = NidooTheme.colors
                    Box(Modifier.matchParentSize().clearAndSetSemantics { contentDescription = description }) {
                        Canvas(Modifier.matchParentSize()) {
                            val k = size.width / frame.w.toFloat()
                            placed.bands.forEach { b -> drawBand(b, frame.vertical, k, byId[b.from], byId[b.to], colors) }
                            placed.bars.forEach { bar ->
                                val n = byId[bar.id] ?: return@forEach
                                val rect = Rect(Offset(bar.x.toFloat() * k, bar.y.toFloat() * k), Size(bar.w.toFloat() * k, bar.h.toFloat() * k))
                                when (n.role) {
                                    SankeyRole.UNITEMISED -> hatch(Path().apply { addRect(rect) }, k, colors)
                                    SankeyRole.HOUSE -> {
                                        drawRoundRect(colors.energyMuted, rect.topLeft, rect.size, CornerRadius(2 * k))
                                        placed.houseMix.forEach { m ->
                                            val from = byId[m.from] ?: return@forEach
                                            val seg = if (frame.vertical) {
                                                Rect(rect.left + rect.width * m.start.toFloat(), rect.top, rect.left + rect.width * m.end.toFloat(), rect.bottom)
                                            } else {
                                                Rect(rect.left, rect.top + rect.height * m.start.toFloat(), rect.right, rect.top + rect.height * m.end.toFloat())
                                            }
                                            drawRect(sankeyColor(from.role, from.slot, colors), seg.topLeft, seg.size)
                                        }
                                    }
                                    else -> drawRoundRect(sankeyColor(n.role, n.slot, colors), rect.topLeft, rect.size, CornerRadius(2 * k))
                                }
                            }
                        }
                        placed.labels.forEach { l ->
                            val n = byId[l.id] ?: return@forEach
                            FluxLabel(n, locale, Modifier.anchored(unit * l.x.toFloat(), unit * (l.y - SankeyLayout.LINE * 0.8).toFloat(), l.anchor))
                        }
                    }
                    // Touch targets, one per bar, over the drawing (the cards are the accessible path to the same details).
                    placed.bars.forEach { bar ->
                        if (bar.id == "unitemised") return@forEach
                        val slopMain = if (frame.vertical) bar.h + 2 * BAR_SLOP else bar.h
                        val slopCross = if (frame.vertical) bar.w else bar.w + 2 * BAR_SLOP
                        val x = if (frame.vertical) bar.x else bar.x - BAR_SLOP
                        val y = if (frame.vertical) bar.y - BAR_SLOP else bar.y
                        Box(
                            Modifier
                                .offset(unit * x.toFloat(), unit * y.toFloat())
                                .size(unit * slopCross.toFloat(), unit * slopMain.toFloat())
                                .testTag("flux-${bar.id}")
                                .pointerInput(bar.id) { detectTapGestures { tap(bar.id) } },
                        )
                    }
                }
            }
        }
        NotReportingCaption(state.notReporting())
    }
}

/** A label's two lines (value, then name), placed by its anchor at ([x], [top]). */
@Composable
private fun FluxLabel(n: SankeyNode, locale: Locale, modifier: Modifier) {
    val align = Alignment.CenterHorizontally
    Column(modifier, horizontalAlignment = align) {
        Text(EnergyFormat.power(n.watts, locale), style = NidooTheme.type.label, color = NidooTheme.colors.content, maxLines = 1)
        Text(sankeyName(n), style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** What a Sankey node is called: the role word, the consumer's name, "Others (3)", "Unmetered". */
@Composable
internal fun sankeyName(n: SankeyNode): String = when (n.role) {
    SankeyRole.SOLAR -> stringResource(DsR.string.energy_solar)
    SankeyRole.GRID -> stringResource(DsR.string.energy_grid)
    SankeyRole.BATTERY -> stringResource(DsR.string.energy_battery)
    SankeyRole.HOUSE -> stringResource(DsR.string.energy_house)
    SankeyRole.EXPORT -> stringResource(R.string.energy_export)
    SankeyRole.BATTERY_CHARGE -> stringResource(R.string.energy_battery_charge)
    SankeyRole.CONSUMER -> n.name ?: stringResource(DsR.string.energy_consumer)
    SankeyRole.OTHERS -> stringResource(R.string.energy_others) + " (${n.count})"
    SankeyRole.UNITEMISED -> stringResource(R.string.energy_unitemised)
}

/** Places the content so that ([x], [top]) is its top-left, top-centre or top-right corner. */
private fun Modifier.anchored(x: Dp, top: Dp, anchor: Anchor): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    val dx = when (anchor) {
        Anchor.START -> 0
        Anchor.MIDDLE -> p.width / 2
        Anchor.END -> p.width
    }
    layout(0, 0) { p.place(x.roundToPx() - dx, top.roundToPx()) }
}

private fun DrawScope.drawBand(b: SankeyBand, vertical: Boolean, k: Float, from: SankeyNode?, to: SankeyNode?, colors: NidooColors) {
    fun o(p: Pt) = Offset(p.x.toFloat() * k, p.y.toFloat() * k)
    val a0 = o(b.a0)
    val a1 = o(b.a1)
    val b0 = o(b.b0)
    val b1 = o(b.b1)
    // Control points at the main-axis midpoint, as in the hub's band path.
    fun ctrl(p: Offset, mid: Float) = if (vertical) Offset(p.x, mid) else Offset(mid, p.y)
    val mid = if (vertical) (a0.y + b0.y) / 2 else (a0.x + b0.x) / 2
    val path = Path().apply {
        moveTo(a0.x, a0.y)
        ctrl(a0, mid).let { c1 -> ctrl(b0, mid).let { c2 -> cubicTo(c1.x, c1.y, c2.x, c2.y, b0.x, b0.y) } }
        lineTo(b1.x, b1.y)
        ctrl(b1, mid).let { c1 -> ctrl(a1, mid).let { c2 -> cubicTo(c1.x, c1.y, c2.x, c2.y, a1.x, a1.y) } }
        close()
    }
    if (to?.role == SankeyRole.UNITEMISED) {
        hatch(path, k, colors)
        return
    }
    val start = from?.let { sankeyColor(it.role, it.slot, colors) } ?: colors.energyMuted
    val end = to?.let { sankeyColor(it.role, it.slot, colors) } ?: colors.energyMuted
    // Into the house a band keeps its source's colour; out of it, its sink's.
    val (c0, c1) = when {
        to?.role == SankeyRole.HOUSE -> start to start
        from?.role == SankeyRole.HOUSE -> end to end
        else -> start to end
    }
    drawPath(path, Brush.linearGradient(listOf(c0, c1), Offset((a0.x + a1.x) / 2, (a0.y + a1.y) / 2), Offset((b0.x + b1.x) / 2, (b0.y + b1.y) / 2)), alpha = 0.38f)
}

/** Stripes at 45° on a light ground: power no consumer meter accounts for, so it never reads as one more device. */
private fun DrawScope.hatch(path: Path, k: Float, colors: NidooColors) {
    drawPath(path, colors.energyHatchGround)
    clipPath(path) {
        val step = 4f * k
        val stroke = 1.6f * k
        var x = -size.height
        while (x < size.width) {
            drawLine(colors.energyHatch, Offset(x, 0f), Offset(x + size.height, size.height), stroke)
            x += step
        }
    }
}
