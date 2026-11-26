package com.gekkotron.nidoo.feature.home.presentation

import androidx.compose.ui.graphics.Color

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/**
 * How far into the song a media player is, `0f..1f`, or null without a duration (media player spec, `position_at`).
 * [positionS] was measured at [positionAtMs]: while playing, the time since then is added, never less than zero, so
 * a `position_at` a little in the future holds the bar instead of moving it back; a null one (the song is loading)
 * holds it at the position.
 */
fun mediaProgress(durationS: Double?, positionS: Double?, positionAtMs: Long?, playing: Boolean, nowMs: Long): Float? {
    if (durationS == null || durationS <= 0.0) return null
    val since = if (playing && positionAtMs != null) maxOf(0L, nowMs - positionAtMs) / 1000.0 else 0.0
    val elapsed = (positionS ?: 0.0) + since
    return (elapsed / durationS).toFloat().coerceIn(0f, 1f)
}

/** The hub's cover colour, `#rrggbb`, as a [Color]; anything else is no colour. */
fun parseTint(color: String?): Color? {
    if (color == null || !HEX.matches(color)) return null
    return Color(0xFF000000 or color.substring(1).toLong(16))
}

/** The one button a card shows: pause while playing, else play; null when the player lacks it. */
fun mediaToggle(state: String?, features: List<String>): String? {
    val action = if (state == "playing") "pause" else "play"
    return action.takeIf { it in features }
}
