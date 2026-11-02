package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.energy.presentation.EnergyFormat
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode
import com.gekkotron.nidoo.feature.energy.presentation.EnergySankey
import com.gekkotron.nidoo.feature.energy.presentation.EnergyUiState
import com.gekkotron.nidoo.feature.energy.presentation.OrbiteArc
import com.gekkotron.nidoo.feature.energy.presentation.OrbiteLayout
import com.gekkotron.nidoo.feature.energy.presentation.Ring
import com.gekkotron.nidoo.feature.energy.presentation.Rings
import com.gekkotron.nidoo.feature.energy.presentation.notReporting
import com.gekkotron.nidoo.feature.energy.presentation.tapTarget
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import com.gekkotron.nidoo.designsystem.R as DsR

/** Ring radii and stroke widths, as shares of the square's side. */
internal object OrbiteGeometry {
    const val OUTER_R = 0.42f
    const val OUTER_W = 0.06f
    const val INNER_R = 0.31f
    const val INNER_W = 0.04f

    /** How far past a ring's stroke a finger still counts, as a share of the side. */
    const val SLOP = 0.03f
}

/** The arc id under ([x], [y]) in a square of [side], or null between the rings and in the middle. */
internal fun orbiteHit(rings: Rings, x: Float, y: Float, side: Float): String? {
    val c = side / 2
    val r = hypot(x - c, y - c)
    val deg = (Math.toDegrees(atan2((x - c).toDouble(), (c - y).toDouble())) + 360.0).mod(360.0)
    fun on(radius: Float, width: Float) = abs(r - radius * side) <= (width / 2 + OrbiteGeometry.SLOP) * side
    val ring = when {
        on(OrbiteGeometry.OUTER_R, OrbiteGeometry.OUTER_W) -> rings.outer
        on(OrbiteGeometry.INNER_R, OrbiteGeometry.INNER_W) -> rings.inner
        else -> return null
    }
    val g = OrbiteLayout.GAP_DEG
    return ring.firstOrNull { deg >= it.a0 - g && deg <= it.a1 + g }?.id
}

/**
 * The Orbite view: inner ring = where power comes from, outer = where it goes, dots drifting along
 * the arcs, the house total in the middle (spec "Energy views").
 */
@Composable
internal fun OrbiteDiagram(state: EnergyUiState, locale: Locale, onMore: () -> Unit, onNode: (EnergyNode) -> Unit, modifier: Modifier = Modifier) {
    val sankey = remember(state) { EnergySankey.build(state) }
    val rings = remember(sankey) { OrbiteLayout.rings(sankey) }
    val description = energyDescription(state, locale)
    val tap: (String) -> Unit = { id -> if (id == "others") onMore() else state.tapTarget(id)?.let(onNode) }
    val arcs = rings.inner + rings.outer
    // The frame clock runs only while there are arcs; time is read in the draw phase (no recomposition per frame).
    val animate = animationsEnabled() && arcs.isNotEmpty()
    val seconds = remember { mutableDoubleStateOf(0.0) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { seconds.doubleValue = (it - start) / 1e9 }
        }
    }
    // Per-arc phase, so a new reading changes the dots' speed, never where they are.
    val phases = remember { HashMap<String, Double>() }
    val last = remember { doubleArrayOf(0.0) }
    val colors = NidooTheme.colors
    Column(modifier.fillMaxWidth().testTag("energy-orbite")) {
        if (arcs.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .testTag("energy-orbite-rings")
                    .pointerInput(rings) { detectTapGestures { o -> orbiteHit(rings, o.x, o.y, size.width.toFloat())?.let(tap) } },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize().clearAndSetSemantics { contentDescription = description }) {
                    val side = size.minDimension
                    val centre = Offset(size.width / 2, size.height / 2)
                    fun ring(rFrac: Float, wFrac: Float, list: List<OrbiteArc>) {
                        val r = rFrac * side
                        val stroke = Stroke(width = wFrac * side)
                        drawCircle(colors.energyMuted.copy(alpha = 0.3f), r, centre, style = stroke)
                        list.forEach { a ->
                            drawArc(
                                sankeyColor(a.role, a.slot, colors),
                                startAngle = (a.a0 - 90).toFloat(),
                                sweepAngle = (a.a1 - a.a0).toFloat(),
                                useCenter = false,
                                topLeft = Offset(centre.x - r, centre.y - r),
                                size = Size(2 * r, 2 * r),
                                style = stroke,
                            )
                        }
                    }
                    ring(OrbiteGeometry.OUTER_R, OrbiteGeometry.OUTER_W, rings.outer)
                    ring(OrbiteGeometry.INNER_R, OrbiteGeometry.INNER_W, rings.inner)
                    if (animate) {
                        val t = seconds.doubleValue
                        val dt = (t - last[0]).coerceAtLeast(0.0)
                        last[0] = t
                        val dot = 2.5.dp.toPx()
                        arcs.forEach { a ->
                            val phase = OrbiteLayout.advancePhase(phases[a.id] ?: 0.0, dt, a)
                            phases[a.id] = phase
                            val r = (if (a.ring == Ring.OUTER) OrbiteGeometry.OUTER_R else OrbiteGeometry.INNER_R) * side
                            OrbiteLayout.arcDots(a, phase).forEach { (deg, opacity) ->
                                val (x, y) = OrbiteLayout.point(centre.x.toDouble(), centre.y.toDouble(), r.toDouble(), deg)
                                drawCircle(Color.White.copy(alpha = opacity.toFloat()), dot, Offset(x.toFloat(), y.toFloat()))
                            }
                        }
                    }
                }
                sankey.houseW?.let { w ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(EnergyFormat.power(w, locale), style = NidooTheme.type.cardValue, color = colors.content)
                        Text(stringResource(DsR.string.energy_house), style = NidooTheme.type.label, color = colors.contentMuted)
                    }
                }
            }
        }
        NotReportingCaption(state.notReporting())
    }
}
