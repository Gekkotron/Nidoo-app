package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.data.EnergyViewPreference
import com.gekkotron.nidoo.designsystem.NidooColors
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.ChipItem
import com.gekkotron.nidoo.designsystem.components.RoomChips
import com.gekkotron.nidoo.feature.settings.R
import kotlin.math.PI

/** Settings › Energy view: three chips and a small sketch of the chosen view (the hub's Appearance pattern). */
@Composable
internal fun EnergyViewSection(selected: EnergyViewPreference, onSelect: (EnergyViewPreference) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        ChipItem(EnergyViewPreference.CLASSIC.name, stringResource(R.string.settings_energy_view_classic)),
        ChipItem(EnergyViewPreference.FLUX.name, stringResource(R.string.settings_energy_view_flux)),
        ChipItem(EnergyViewPreference.ORBITE.name, stringResource(R.string.settings_energy_view_orbite)),
    )
    Column(modifier) {
        Text(stringResource(R.string.settings_energy_view_hint), style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted)
        RoomChips(items, selected.name, onSelect = { key -> EnergyViewPreference.entries.firstOrNull { it.name == key }?.let(onSelect) }, Modifier.padding(top = 8.dp))
        EnergyViewSketch(selected, Modifier.padding(top = 8.dp).widthIn(max = 320.dp).fillMaxWidth())
    }
}

/** A fixed sketch of [view] with sample shapes; decorative (the chips carry the meaning). Port of the hub's `energy_preview.rs`. */
@Composable
internal fun EnergyViewSketch(view: EnergyViewPreference, modifier: Modifier = Modifier) {
    val colors = NidooTheme.colors
    // The hub's viewBox: 0 -8 150 112.
    Canvas(modifier.aspectRatio(150f / 112f).clearAndSetSemantics {}) {
        val k = size.width / 150f
        fun p(x: Float, y: Float) = Offset(x * k, (y + 8f) * k)
        when (view) {
            EnergyViewPreference.CLASSIC -> classic(colors, k, ::p)
            EnergyViewPreference.FLUX -> flux(colors, k, ::p)
            EnergyViewPreference.ORBITE -> orbite(colors, k, ::p)
        }
    }
}

private fun DrawScope.node(c: Offset, r: Float, colour: Color, k: Float) {
    drawCircle(colour.copy(alpha = 0.12f), (r + 5) * k, c)
    drawCircle(colour.copy(alpha = 0.22f), r * k, c)
    drawCircle(colour, r * k, c, style = Stroke(1.6f * k))
}

private fun DrawScope.wire(from: Offset, ctrl: Offset, to: Offset, colour: Color, k: Float) {
    val path = Path().apply { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
    drawPath(path, colour.copy(alpha = 0.55f), style = Stroke(1.6f * k))
}

private fun DrawScope.classic(c: NidooColors, k: Float, p: (Float, Float) -> Offset) {
    val (c0, c1, c2) = c.energyConsumers
    wire(p(38f, 28f), p(60f, 30f), p(64f, 42f), c.solar, k)
    wire(p(38f, 70f), p(60f, 68f), p(64f, 56f), c.grid, k)
    wire(p(90f, 44f), p(104f, 28f), p(118f, 24f), c0, k)
    wire(p(92f, 48f), p(105f, 48f), p(118f, 48f), c1, k)
    wire(p(90f, 54f), p(104f, 68f), p(118f, 72f), c2, k)
    node(p(28f, 26f), 10f, c.solar, k)
    node(p(28f, 72f), 10f, c.grid, k)
    node(p(77f, 48f), 16f, c.content, k)
    node(p(126f, 24f), 7f, c0, k)
    node(p(126f, 48f), 7f, c1, k)
    node(p(126f, 72f), 7f, c2, k)
}

private fun DrawScope.flux(c: NidooColors, k: Float, p: (Float, Float) -> Offset) {
    val (c0, c1, c2) = c.energyConsumers
    fun band(x1: Float, a0: Float, a1: Float, x2: Float, b0: Float, b1: Float): Path {
        val m = (x1 + x2) / 2
        return Path().apply {
            p(x1, a0).let { moveTo(it.x, it.y) }
            val (q1, q2, q3) = Triple(p(m, a0), p(m, b0), p(x2, b0))
            cubicTo(q1.x, q1.y, q2.x, q2.y, q3.x, q3.y)
            p(x2, b1).let { lineTo(it.x, it.y) }
            val (r1, r2, r3) = Triple(p(m, b1), p(m, a1), p(x1, a1))
            cubicTo(r1.x, r1.y, r2.x, r2.y, r3.x, r3.y)
            close()
        }
    }
    fun bar(x: Float, y: Float, h: Float, colour: Color) = drawRoundRect(colour, p(x, y), Size(6f * k, h * k), CornerRadius(2f * k))
    drawPath(band(16f, 12f, 58f, 70f, 12f, 58f), c.solar.copy(alpha = 0.38f))
    drawPath(band(16f, 64f, 82f, 70f, 60f, 78f), c.grid.copy(alpha = 0.38f))
    drawPath(band(76f, 12f, 40f, 132f, 8f, 36f), c0.copy(alpha = 0.38f))
    drawPath(band(76f, 40f, 56f, 132f, 42f, 58f), c1.copy(alpha = 0.38f))
    drawPath(band(76f, 56f, 66f, 132f, 64f, 74f), c2.copy(alpha = 0.38f))
    // What no consumer accounts for is hatched, as on the Energy screen.
    val rest = band(76f, 66f, 78f, 132f, 80f, 92f)
    drawPath(rest, c.energyHatchGround)
    clipPath(rest) {
        var x = 0f
        while (x < size.width + size.height) {
            drawLine(c.energyHatch, Offset(x, 0f), Offset(x - size.height, size.height), 1.6f * k)
            x += 4f * k
        }
    }
    bar(10f, 12f, 46f, c.solar)
    bar(10f, 64f, 18f, c.grid)
    bar(70f, 12f, 46f, c.solar)
    bar(70f, 58f, 20f, c.grid)
    bar(132f, 8f, 28f, c0)
    bar(132f, 42f, 16f, c1)
    bar(132f, 64f, 10f, c2)
    bar(132f, 80f, 12f, c.energyHatch)
}

private fun DrawScope.orbite(c: NidooColors, k: Float, p: (Float, Float) -> Offset) {
    val (c0, c1, c2) = c.energyConsumers
    val centre = p(75f, 48f)
    fun ring(r: Float, width: Float, parts: List<Pair<Float, Color>>) {
        drawCircle(c.energyMuted.copy(alpha = 0.3f), r * k, centre, style = Stroke(width * k))
        val gap = (1.4f / (2 * PI.toFloat() * r)) * 360f
        var start = -90f
        parts.forEach { (share, colour) ->
            drawArc(colour, start, share * 360f - gap, false, Offset(centre.x - r * k, centre.y - r * k), Size(2 * r * k, 2 * r * k), style = Stroke(width * k))
            start += share * 360f
        }
    }
    ring(38f, 9f, listOf(0.10f to c.connected, 0.48f to c0, 0.21f to c1, 0.08f to c2, 0.13f to c.energyMuted))
    ring(27f, 6f, listOf(0.85f to c.solar, 0.15f to c.grid))
}
