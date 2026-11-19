package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.gekkotron.nidoo.designsystem.NidooColors
import com.gekkotron.nidoo.designsystem.NidooTheme
import kotlin.math.PI
import kotlin.math.sin

/** What an air conditioner is doing; the icon's colour and airflow follow it. */
enum class AcMode { COOL, HEAT, DRY, FAN, AUTO }

/** The mode's colour from the theme; fan only uses the card's content colour ([neutral]). */
fun acModeColor(mode: AcMode, colors: NidooColors, neutral: Color): Color = when (mode) {
    AcMode.COOL -> colors.water
    AcMode.HEAT -> colors.heat
    AcMode.DRY -> colors.water
    AcMode.FAN -> neutral
    AcMode.AUTO -> colors.connected
}

/** Time for the airflow to travel one dash period; faster at higher fan speed, baseline for anything unknown. */
fun airflowPeriodMs(fanMode: String?): Int = when (fanMode?.lowercase()) {
    "silence", "silent", "quiet", "low", "1" -> 2400
    "2" -> 2000
    "medium", "med", "3" -> 1600
    "4" -> 1300
    "high", "5" -> 1000
    else -> 1800
}

/**
 * The home-tile air conditioner: a wall unit whose vane flips and whose airflow streams down in the
 * mode's colour (drops while drying). [mode] null is off. Every animated value is a [State] read only in
 * the draw lambda, so a running AC never recomposes its card per frame.
 */
@Composable
fun AcIcon(mode: AcMode?, fanMode: String? = null, animate: Boolean = true, modifier: Modifier = Modifier) {
    val running = mode != null
    val moving = running && animate
    val vanePhase = if (moving) rememberLoopPhase(3200) else STILL_VANE
    val flowPhase = if (moving && mode != AcMode.DRY) rememberLoopPhase(airflowPeriodMs(fanMode)) else STILL_FLOW
    val dropPhase = if (moving && mode == AcMode.DRY) rememberLoopPhase(1400) else STILL_DROPS
    val colors = NidooTheme.colors
    IconCanvas(modifier) { neutral ->
        val tint = if (mode == null) neutral else acModeColor(mode, colors, neutral)
        drawUnit(neutral, tint, running)
        // Off: the vane hangs closed, neutral. Running: it swings in the mode's colour (still when animations are off).
        drawVane(if (mode == null) neutral else tint, if (mode != null && animate) vaneY(vanePhase.value) else VANE_REST_Y)
        if (mode == null) return@IconCanvas
        if (mode == AcMode.DRY) drawDrops(tint, dropPhase.value, animate) else drawAirflow(tint, flowPhase.value, animate)
    }
}

private val STILL_VANE: State<Float> = mutableFloatStateOf(0f)
private val STILL_FLOW: State<Float> = mutableFloatStateOf(0f)
private val STILL_DROPS: State<Float> = mutableFloatStateOf(0f)

private const val UNIT_LEFT = 2.5f
private const val UNIT_TOP = 4f
private const val UNIT_RIGHT = 21.5f
private const val UNIT_BOTTOM = 12.4f
private const val FLOW_TOP = 14.2f
private const val FLOW_BOTTOM = 24f
private const val FLOW_PERIOD = 5f

private val FLOW_COLUMNS = floatArrayOf(7f, 12f, 17f)

private val UNIT_PATH: Path by lazy {
    Path().apply {
        addRoundRect(RoundRect(UNIT_LEFT, UNIT_TOP, UNIT_RIGHT, UNIT_BOTTOM, CornerRadius(2.6f)))
    }
}

private val DROP_PATHS: List<Path> by lazy {
    FLOW_COLUMNS.map { x ->
        PathParser().parsePathString("M$x 14.6c.8 1.1 1.3 1.8 1.3 2.4a1.3 1.3 0 0 1-2.6 0c0-.6.5-1.3 1.3-2.4z").toPath()
    }
}

private val AIRFLOW_PATHS: List<Path> by lazy {
    FLOW_COLUMNS.map { x ->
        PathParser().parsePathString("M$x $FLOW_TOP c-1.1 1.6 1.1 3.2 0 4.8 s1.1 3.2 0 4.8").toPath()
    }
}

private fun DrawScope.drawUnit(neutral: Color, tint: Color, running: Boolean) {
    surface(UNIT_PATH, neutral)
    drawLine(neutral, Offset(5.5f, 7.3f), Offset(14.5f, 7.3f), strokeWidth = 1f, cap = StrokeCap.Round, alpha = 0.55f)
    drawCircle(tint, radius = 0.85f, center = Offset(18.2f, 7.3f), alpha = if (running) 1f else 0.3f)
}

/**
 * The vane's height for a swing [phase] in 0..1: it hangs just below the unit (clear of its 1.6-wide
 * bottom outline) and swings 0.4 unit down and up, like a louvre sweeping the air.
 */
internal fun vaneY(phase: Float): Float = VANE_REST_Y + VANE_SWING * sin(2f * PI.toFloat() * phase)

private const val VANE_REST_Y = 14.3f
private const val VANE_SWING = 0.4f

private fun DrawScope.drawVane(color: Color, y: Float) {
    drawLine(color, Offset(6f, y), Offset(18f, y), strokeWidth = 1.3f, cap = StrokeCap.Round)
}

/** Three wavy streams fading toward the bottom; solid when [animate] is false, dashes marching down otherwise. */
private fun DrawScope.drawAirflow(color: Color, phase: Float, animate: Boolean) {
    val bounds = Rect(0f, 0f, 24f, 24f)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint())
        AIRFLOW_PATHS.forEachIndexed { k, path ->
            val effect = if (animate) {
                PathEffect.dashPathEffect(floatArrayOf(2.6f, 2.4f), -2f * FLOW_PERIOD * phase - k * 2f * FLOW_PERIOD / 3f)
            } else {
                null
            }
            drawPath(path, color, style = Stroke(1.5f, cap = StrokeCap.Round, pathEffect = effect))
        }
        drawRect(
            Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = 13f, endY = FLOW_BOTTOM),
            topLeft = bounds.topLeft,
            size = bounds.size,
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
}

/** Three drops falling 7 units and fading, staggered by a third of the cycle. */
private fun DrawScope.drawDrops(color: Color, phase: Float, animate: Boolean) {
    DROP_PATHS.forEachIndexed { k, path ->
        val base = if (animate) phase else STILL_DROP_PHASE
        val p = (base + k / 3f) % 1f
        val alpha = if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f
        translate(top = 7f * FastOutLinearInEasing.transform(p)) {
            drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}

private const val STILL_DROP_PHASE = 0.4f
