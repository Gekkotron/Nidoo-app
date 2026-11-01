package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class NidooColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val cardOnStart: Color,
    val cardOnEnd: Color,
    val cardOnBorder: Color,
    val content: Color,
    val contentMuted: Color,
    val contentOnCardOn: Color,
    val chip: Color,
    val chipBorder: Color,
    val chipSelected: Color,
    val chipSelectedContent: Color,
    val connected: Color,
    val connectedContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val offline: Color,
    val offlineContainer: Color,
    val solar: Color,
    val water: Color,
    val grid: Color,
    val heat: Color,
    val shadow: Color,
)

private val Ink = Color(0xFFE8ECFF)
private val Night = Color(0xFF0B1020)
private val Amber = Color(0xFFFFC457)
private val Mint = Color(0xFF6FF0BF)
private val Rose = Color(0xFFFF7A9A)

/** Spec §5.1, mockup `visual-style.html` option A. */
val NightGlassColors = NidooColors(
    isDark = true,
    backgroundTop = Color(0xFF1D2B55),
    backgroundBottom = Night,
    card = Color.White.copy(alpha = 0.06f),
    cardBorder = Color.White.copy(alpha = 0.09f),
    cardOnStart = Amber.copy(alpha = 0.28f),
    cardOnEnd = Amber.copy(alpha = 0.06f),
    cardOnBorder = Amber.copy(alpha = 0.35f),
    content = Ink,
    contentMuted = Ink.copy(alpha = 0.65f),
    contentOnCardOn = Ink,
    chip = Color.White.copy(alpha = 0.07f),
    chipBorder = Color.Transparent,
    chipSelected = Ink,
    chipSelectedContent = Night,
    connected = Mint,
    connectedContainer = Color(0xFF50E6AA).copy(alpha = 0.15f),
    warning = Amber,
    warningContainer = Amber.copy(alpha = 0.15f),
    offline = Color(0xFFFF9AB2),
    offlineContainer = Rose.copy(alpha = 0.15f),
    solar = Amber,
    water = Color(0xFF58E1FF),
    grid = Color(0xFF8F7BFF),
    heat = Rose,
    shadow = Color.Transparent,
)

/** Ivoire, ported from the Nidoo-OS web tokens (`tokens.css`, `[data-theme="light"]`). */
val IvoireColors = NidooColors(
    isDark = false,
    backgroundTop = Color(0xFFF3F0E8),
    backgroundBottom = Color(0xFFF3F0E8),
    card = Color(0xFFFFFDF8),
    cardBorder = Color(0xFFEBE6DA),
    cardOnStart = Color(0xFFFBF0D4),
    cardOnEnd = Color(0xFFFBF0D4),
    cardOnBorder = Color(0xFFC9973D).copy(alpha = 0.45f),
    content = Color(0xFF1F2420),
    contentMuted = Color(0xFF5F665C),
    contentOnCardOn = Color(0xFF5C4208),
    chip = Color.Transparent,
    chipBorder = Color(0xFFD9D0C2),
    chipSelected = Color(0xFFE3EADF),
    chipSelectedContent = Color(0xFF2F4A2B),
    connected = Color(0xFF4F6B4A),
    connectedContainer = Color(0xFFE3EADF),
    warning = Color(0xFF8A5F12),
    warningContainer = Color(0xFFFBF0D4),
    offline = Color(0xFFA23B4F),
    offlineContainer = Color(0xFFF6DDE2),
    solar = Color(0xFFC9973D),
    water = Color(0xFF38738F),
    grid = Color(0xFF6A5ACD),
    heat = Color(0xFFAC512B),
    shadow = Color(0xFF3C3214).copy(alpha = 0.10f),
)
