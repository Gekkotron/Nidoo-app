package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.RoundRect
import com.gekkotron.nidoo.designsystem.NidooTheme

/** What the vacuum tile is showing: still, cleaning the room in rows, heading home, or charging in its dock. */
enum class VacuumMotion { IDLE, CLEANING, RETURNING, DOCKED }

/**
 * The home-tile robot vacuum: a robot disc in a room. Cleaning drives it along the rows, leaving a trail,
 * at a pace that follows [fanSpeed]; returning drives it into a dock in the corner; docked shows it
 * charging. [accent] tints the robot while cleaning or returning (the card's content colour otherwise).
 * Every animated value is a [State] read only in the draw lambda, so a running robot never recomposes
 * its card per frame.
 */
@Composable
fun VacuumIcon(
    motion: VacuumMotion,
    fanSpeed: String? = null,
    accent: Color? = null,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val moving = animate
    val rowPhase = if (moving && motion == VacuumMotion.CLEANING) loopPhase("vacuum-rows", rowPeriodMs(fanSpeed)) else STILL_ROWS
    val dockPhase = if (moving && motion == VacuumMotion.RETURNING) returnPhase() else STILL_RETURN
    val boltAlpha = if (moving && motion == VacuumMotion.DOCKED) boltPhase() else STILL_BOLT
    val connected = NidooTheme.colors.connected
    IconCanvas(modifier) { neutral ->
        drawRoom(neutral)
        when (motion) {
            VacuumMotion.IDLE -> drawRobot(RobotPath.poseAt(0f), neutral, IDLE_FILL)
            VacuumMotion.CLEANING -> {
                val tint = accent ?: neutral
                val phase = rowPhase.value
                outlineTrail(RobotPath.trailTo(phase), tint)
                drawRobot(RobotPath.poseAt(phase), tint)
            }
            VacuumMotion.RETURNING -> {
                drawDock(neutral)
                val p = dockPhase.value
                val center = Offset(
                    RETURN_FROM.x + (RETURN_TO.x - RETURN_FROM.x) * p,
                    RETURN_FROM.y + (RETURN_TO.y - RETURN_FROM.y) * p,
                )
                drawRobot(RobotPose(center, RETURN_HEADING), accent ?: neutral)
            }
            VacuumMotion.DOCKED -> {
                drawDock(connected, filled = true)
                drawRobot(RobotPose(RETURN_TO, RETURN_HEADING), neutral, IDLE_FILL)
                drawPath(BOLT_PATH, connected, alpha = boltAlpha.value)
            }
        }
    }
}

private val STILL_ROWS: State<Float> = mutableFloatStateOf(0.5f)
private val STILL_RETURN: State<Float> = mutableFloatStateOf(0.5f)
private val STILL_BOLT: State<Float> = mutableFloatStateOf(0.9f)

private val RETURN_FROM = Offset(6.5f, 8.5f)
private val RETURN_TO = Offset(19.5f, 19f)
private const val RETURN_HEADING = 39f
private const val RETURN_CYCLE_MS = 3000
private const val RETURN_ARRIVE_MS = 2250
private const val IDLE_FILL = 0.22f
private const val ROBOT_FILL = 0.35f
private const val ROBOT_RADIUS = 3.1f

private val BOLT_PATH: Path by lazy {
    PathParser().parsePathString("M12.3 8 L10 12 h2.5 l-.8 3.8 L14.4 11.5 H11.9 L12.8 8z").toPath()
}

@Composable
private fun loopPhase(label: String, periodMs: Int, easing: Easing = LinearEasing): State<Float> =
    rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = easing)),
        label = "phase",
    )

/** 0 to 1 as the robot drives into its dock, then holds there until the loop restarts. */
@Composable
private fun returnPhase(): State<Float> =
    rememberInfiniteTransition(label = "vacuum-return").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = RETURN_CYCLE_MS
                0f at 0 using FastOutSlowInEasing
                1f at RETURN_ARRIVE_MS
                1f at RETURN_CYCLE_MS
            },
        ),
        label = "phase",
    )

@Composable
private fun boltPhase(): State<Float> =
    rememberInfiniteTransition(label = "vacuum-bolt").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )

private fun DrawScope.drawRoom(color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(1.5f, 2f),
        size = Size(21f, 20.5f),
        cornerRadius = CornerRadius(3f),
        style = Stroke(1f),
        alpha = 0.28f,
    )
}

private fun DrawScope.outlineTrail(points: List<Offset>, color: Color) {
    drawPath(
        polygon(points, close = false),
        color,
        alpha = 0.16f,
        style = Stroke(4.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A disc with a front bumper arc and a centre puck, turned to face [RobotPose.headingDeg]. */
private fun DrawScope.drawRobot(pose: RobotPose, color: Color, fillAlpha: Float = ROBOT_FILL) {
    rotate(pose.headingDeg, pivot = pose.center) {
        drawCircle(color, ROBOT_RADIUS, pose.center, alpha = fillAlpha)
        drawCircle(color, ROBOT_RADIUS, pose.center, style = Stroke(1.3f))
        val r = 2.9f
        drawArc(
            color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(pose.center.x - r, pose.center.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(1.3f, cap = StrokeCap.Round),
        )
        drawCircle(color, 1.05f, pose.center)
    }
}

private fun DrawScope.drawDock(color: Color, filled: Boolean = false) {
    val dock = Path().apply { addRoundRect(RoundRect(18f, 16.5f, 22f, 21.5f, CornerRadius(1f))) }
    if (filled) {
        drawPath(dock, color, alpha = IDLE_FILL)
        outline(dock, color, width = 1.4f)
    } else {
        surface(dock, color)
    }
}
