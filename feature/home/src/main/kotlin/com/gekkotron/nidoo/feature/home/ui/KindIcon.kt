package com.gekkotron.nidoo.feature.home.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.icons.DoorIcon
import com.gekkotron.nidoo.designsystem.icons.GarageIcon
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.ShutterIcon
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.designsystem.icons.VacuumIcon
import com.gekkotron.nidoo.designsystem.icons.VacuumMotion
import com.gekkotron.nidoo.designsystem.icons.WindowIcon
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.model.AlarmState
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Hs
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.SwitchType
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class SwingShape { DOOR, WINDOW, GARAGE }

/** What a card's icon draws (device-icons spec §4.3), before animation. */
internal sealed interface IconSpec {
    /** [target] null: no position and not OPEN/CLOSED, keep the drawn value. */
    data class Shutter(val target: Float?, val moving: CoverState?) : IconSpec

    /** [open] null: unknown or unavailable, drawn neutral and closed. */
    data class Swing(val shape: SwingShape, val open: Boolean?) : IconSpec

    data class Static(val glyph: NidooGlyph, val on: Boolean) : IconSpec

    /** Climate tile: a spinning fan while [running], SNOWFLAKE otherwise; period follows [fanMode]. */
    data class Climate(val running: Boolean, val fanMode: String?) : IconSpec

    /** Vacuum tile: the side brush spins while cleaning (dust puffs trail) and the robot nudges home while returning. */
    data class Vacuum(val motion: VacuumMotion, val fanSpeed: String?) : IconSpec

    /** A siren switch or an alarm panel: [active] only while it is sounding or triggered; the glyph pulses then. */
    data class Siren(val glyph: NidooGlyph, val active: Boolean) : IconSpec

    /** A light: a soft glow while [on] and [available], tinted with [color] when the light reports one. */
    data class Light(val on: Boolean, val color: Hs?, val available: Boolean) : IconSpec
}

/** An open door, window or garage door: the card gets the accent border. */
internal val IconSpec.openContact: Boolean get() = this is IconSpec.Swing && open == true

internal fun iconSpec(card: CardUi): IconSpec = when (card.kind) {
    Kind.COVER -> (card.value as? CardValue.CoverGroup)?.let { g ->
        IconSpec.Shutter(g.positions.average().toFloat() / 100f, g.moving.takeIf { card.available })
    } ?: shutter(card.value as? CardValue.Cover, card.available)
    Kind.BINARY_SENSOR -> swingShape(card.deviceClass)
        ?.let { IconSpec.Swing(it, if (card.available) (card.value as? CardValue.Contact)?.on else null) }
        ?: IconSpec.Static(binaryGlyph(card.deviceClass), on = false)
    Kind.LIGHT -> IconSpec.Light(card.on, (card.state as? StateValue.Light)?.color, card.available)
    Kind.SWITCH -> switchIconSpec(card)
    Kind.WATER_HEATER -> IconSpec.Static(NidooGlyph.WATER_HEATER, card.on)
    Kind.CLIMATE -> IconSpec.Climate(running = card.available && card.on, fanMode = (card.state as? StateValue.Climate)?.fanMode)
    Kind.SENSOR -> IconSpec.Static(sensorGlyph(card.deviceClass), on = false)
    Kind.ALARM_PANEL -> IconSpec.Siren(
        NidooGlyph.SHIELD,
        active = card.available && (card.state as? StateValue.Alarm)?.state == AlarmState.TRIGGERED,
    )
    Kind.NUMBER -> IconSpec.Static(NidooGlyph.SLIDER, on = false)
    Kind.SELECT -> IconSpec.Static(NidooGlyph.LIST, on = false)
    Kind.BUTTON -> IconSpec.Static(NidooGlyph.PLAY, on = false)
    Kind.VACUUM -> (card.state as? StateValue.Vacuum).let { s ->
        IconSpec.Vacuum(motion = vacuumMotion(s?.state), fanSpeed = s?.fanSpeed)
    }
    Kind.MEDIA_PLAYER -> IconSpec.Static(NidooGlyph.MUSIC, card.on)
}

/** Device sheet spec §5.3: a Z2M switch wears the glyph of its presentation type. */
private fun switchIconSpec(card: CardUi): IconSpec = when (SwitchType.of(card.protocolKind, card.ui)) {
    // The Door opener card reuses the binary sensor's door swing: open while the relay reads on.
    SwitchType.DOOR -> IconSpec.Swing(SwingShape.DOOR, open = if (card.available) card.on else null)
    // Siren is deferred to BOLT until a dedicated glyph lands; it pulses while it sounds.
    SwitchType.SIREN -> IconSpec.Siren(NidooGlyph.BOLT, active = card.available && card.on)
    // Chime reuses PLAY (the Button glyph) — a tap sends a one-shot.
    SwitchType.CHIME -> IconSpec.Static(NidooGlyph.PLAY, on = false)
    // Plug and plain switch keep today's PLUG.
    SwitchType.PLUG, SwitchType.SWITCH, null -> IconSpec.Static(NidooGlyph.PLUG, card.on)
    // Promoted to Kind.LIGHT upstream; this branch is unreachable in practice.
    SwitchType.LIGHT -> IconSpec.Light(card.on, color = null, available = card.available)
}

private fun vacuumMotion(state: String?): VacuumMotion = when (state) {
    "cleaning" -> VacuumMotion.CLEANING
    "returning" -> VacuumMotion.RETURNING
    else -> VacuumMotion.IDLE
}

/** An unavailable cover keeps its last known position (closed could misreport it) but stops moving. */
private fun shutter(v: CardValue.Cover?, available: Boolean): IconSpec.Shutter {
    val target = when {
        v == null -> null
        v.position != null -> v.position.coerceIn(0, 100) / 100f
        v.state == CoverState.OPEN -> 1f
        v.state == CoverState.CLOSED -> 0f
        else -> null
    }
    val moving = v?.state?.takeIf { available && (it == CoverState.OPENING || it == CoverState.CLOSING) }
    return IconSpec.Shutter(target, moving)
}

private fun swingShape(deviceClass: String?) = when (deviceClass) {
    "door", "opening" -> SwingShape.DOOR
    "window" -> SwingShape.WINDOW
    "garage_door" -> SwingShape.GARAGE
    else -> null
}

private fun sensorGlyph(deviceClass: String?) = when (deviceClass) {
    "temperature" -> NidooGlyph.THERMOMETER
    "humidity", "moisture" -> NidooGlyph.DROP
    "power", "energy" -> NidooGlyph.BOLT
    else -> NidooGlyph.TREND
}

private fun binaryGlyph(deviceClass: String?) = when (deviceClass) {
    "motion", "occupancy", "presence" -> NidooGlyph.WALKER
    "smoke" -> NidooGlyph.FLAME
    "moisture" -> NidooGlyph.DROP
    else -> NidooGlyph.DOT
}

internal const val SWING_MS = 500
internal const val SHUTTER_MS = 600
internal const val DRIFT_FULL_MS = 8000
private val SWING_EASING = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)
private val SHUTTER_EASING = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Whether the spec carries a real state; a cached card has none until live data arrives. */
private fun IconSpec.known(): Boolean = when (this) {
    is IconSpec.Shutter -> target != null
    is IconSpec.Swing -> open != null
    is IconSpec.Static -> true
    is IconSpec.Climate -> true
    is IconSpec.Vacuum -> true
    is IconSpec.Siren -> true
    is IconSpec.Light -> true
}

private fun IconSpec.target(): Float? = when (this) {
    is IconSpec.Shutter -> target
    is IconSpec.Swing -> if (open == true) 1f else 0f
    is IconSpec.Static -> null
    is IconSpec.Climate -> null
    is IconSpec.Vacuum -> null
    is IconSpec.Siren -> null
    is IconSpec.Light -> null
}

/**
 * The drawn fraction for [spec] (spec §3): 0 closed … 1 open. The first composition draws the state at
 * once; later changes tween from wherever the drawing is, so a flapping contact turns around instead of
 * jumping. A moving shutter drifts toward its end stop at [DRIFT_FULL_MS] per full travel. With
 * [animate] false every change snaps and nothing drifts.
 */
@Composable
internal fun rememberIconProgress(spec: IconSpec, animate: Boolean): State<Float> {
    val progress = remember { Animatable(spec.target() ?: 0f) }
    val settled = remember { BooleanArray(1) { spec.known() } }
    LaunchedEffect(spec, animate) {
        val target = spec.target()
        // The first real state after a cold start (cached card, no state yet) is drawn at once, like a first composition.
        if (!settled[0] && spec.known()) {
            settled[0] = true
            if (target != null) progress.snapTo(target)
            if (spec !is IconSpec.Shutter || spec.moving == null) return@LaunchedEffect
        }
        if (!animate) {
            if (target != null) progress.snapTo(target)
            return@LaunchedEffect
        }
        when (spec) {
            is IconSpec.Swing -> progress.animateTo(target ?: 0f, tween(SWING_MS, easing = SWING_EASING))
            is IconSpec.Shutter -> {
                // animateTo waits out its whole duration even with nothing to travel, which would delay the drift.
                if (target != null && target != progress.value) progress.animateTo(target, tween(SHUTTER_MS, easing = SHUTTER_EASING))
                val end = when (spec.moving) {
                    CoverState.OPENING -> 1f
                    CoverState.CLOSING -> 0f
                    else -> return@LaunchedEffect
                }
                val ms = (abs(end - progress.value) * DRIFT_FULL_MS).roundToInt()
                if (ms > 0) progress.animateTo(end, tween(ms, easing = LinearEasing))
            }
            is IconSpec.Static -> Unit
            is IconSpec.Climate -> Unit
            is IconSpec.Vacuum -> Unit
            is IconSpec.Siren -> Unit
            is IconSpec.Light -> Unit
        }
    }
    return progress.asState()
}

/** The card icon, decorative: hidden from TalkBack, the card's own description carries the state. */
@Composable
internal fun KindIcon(spec: IconSpec, modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    val progress by rememberIconProgress(spec, animate)
    val c = NidooTheme.colors
    val neutral = LocalContentColor.current
    val m = modifier.clearAndSetSemantics { }
    when (spec) {
        is IconSpec.Shutter -> ShutterIcon(progress, m)
        is IconSpec.Swing -> {
            // Follows the swing itself, mint closed to amber open: it snaps when the swing snaps, and a theme switch never fades.
            val accent = if (spec.open == null) neutral else lerp(c.connected, c.warning, progress)
            when (spec.shape) {
                SwingShape.DOOR -> DoorIcon(progress, accent, m)
                SwingShape.WINDOW -> WindowIcon(progress, accent, m)
                SwingShape.GARAGE -> GarageIcon(progress, accent, m)
            }
        }
        // Ivoire's on-card is pale amber: the bright amber warning would vanish, so a lit glyph uses the card's content color there.
        is IconSpec.Static -> StaticIcon(spec.glyph, if (spec.on) (if (c.isDark) c.warning else neutral) else null, m)
        is IconSpec.Climate -> ClimateIcon(spec.running, spec.fanMode, animate, m)
        is IconSpec.Siren -> SirenIcon(spec, if (c.isDark) c.warning else neutral, animate, m)
        is IconSpec.Light -> LightIcon(spec, if (c.isDark) c.warning else neutral, animate, m)
        is IconSpec.Vacuum -> {
            val active = spec.motion != VacuumMotion.IDLE
            VacuumIcon(spec.motion, spec.fanSpeed, if (active) (if (c.isDark) c.warning else neutral) else null, animate, m)
        }
    }
}

/** On the AC tile: SNOWFLAKE when idle, a spinning FAN while running; period follows fan mode. */
@Composable
private fun ClimateIcon(running: Boolean, fanMode: String?, animate: Boolean, modifier: Modifier) {
    if (!running) {
        StaticIcon(NidooGlyph.SNOWFLAKE, null, modifier)
        return
    }
    val angle = if (animate) {
        val transition = rememberInfiniteTransition(label = "fan")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(fanPeriodMs(fanMode), easing = LinearEasing)),
            label = "angle",
        ).value
    } else {
        0f
    }
    StaticIcon(NidooGlyph.FAN, null, modifier.graphicsLayer { rotationZ = angle })
}

/** A siren pulses its glyph (scale and fade) while sounding; idle or with reduced motion it is drawn still. */
@Composable
private fun SirenIcon(spec: IconSpec.Siren, tint: Color, animate: Boolean, modifier: Modifier) {
    if (!spec.active) {
        StaticIcon(spec.glyph, null, modifier)
        return
    }
    if (!animate) {
        StaticIcon(spec.glyph, tint, modifier)
        return
    }
    val pulse = rememberInfiniteTransition(label = "siren").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // The pulse is read inside the layer block, so each frame only re-runs the draw phase.
    StaticIcon(
        spec.glyph,
        tint,
        modifier.graphicsLayer {
            val t = pulse.value
            scaleX = pulseScale(t)
            scaleY = pulseScale(t)
            alpha = pulseAlpha(t)
        },
    )
}

/** A light that is on glows behind its bulb, in its own colour when it has one; off or unavailable it is plain. */
@Composable
private fun LightIcon(spec: IconSpec.Light, tint: Color, animate: Boolean, modifier: Modifier) {
    val lit = spec.on && spec.available
    if (!lit) {
        StaticIcon(NidooGlyph.BULB, null, modifier)
        return
    }
    val glow = glowColor(spec.color, tint)
    val breath = if (animate) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(GLOW_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        null
    }
    StaticIcon(
        NidooGlyph.BULB,
        tint,
        modifier.drawBehind {
            val alpha = glowAlpha(lit, breath?.value ?: GLOW_STILL)
            val radius = size.minDimension * GLOW_RADIUS
            drawCircle(
                brush = Brush.radialGradient(listOf(glow.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
                radius = radius,
            )
        },
    )
}

internal const val PULSE_HALF_MS = 600
internal const val GLOW_HALF_MS = 1800
private const val GLOW_RADIUS = 0.95f
internal const val GLOW_STILL = 0.5f

/** Siren glyph scale for a pulse phase [t] in 0..1. */
internal fun pulseScale(t: Float): Float = 1f + 0.14f * t.coerceIn(0f, 1f)

/** Siren glyph opacity for a pulse phase [t] in 0..1: it dims as it swells. */
internal fun pulseAlpha(t: Float): Float = 1f - 0.35f * t.coerceIn(0f, 1f)

/** Peak glow opacity for breath phase [t] in 0..1; zero when the light is not [lit]. */
internal fun glowAlpha(lit: Boolean, t: Float): Float = if (lit) 0.22f + 0.2f * t.coerceIn(0f, 1f) else 0f

/** The glow follows the light's own colour when it reports one, else the on-tint. */
internal fun glowColor(color: Hs?, fallback: Color): Color =
    color?.let { Color.hsv(it.hue.toFloat(), it.saturation.toFloat(), 1f) } ?: fallback

private fun fanPeriodMs(fanMode: String?): Int = when (fanMode?.lowercase()) {
    "silence", "silent", "quiet", "low", "1" -> 2400
    "2" -> 2000
    "medium", "med", "3" -> 1600
    "4" -> 1300
    "high", "5" -> 1000
    else -> 1800
}

@Composable
private fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}
