package com.gekkotron.nidoo.feature.energy.presentation

/**
 * Geometry of the Sankey model: where each bar, band and label sits in a frame, horizontal on wide
 * screens and vertical on phones. Pure; the Flux view scales it onto its Canvas. A port of the hub's
 * `sankey_layout.rs` (Oikos-os 1226316), emitting points instead of SVG paths.
 */
data class Frame(val w: Double, val h: Double, val vertical: Boolean) {
    companion object {
        val WIDE = Frame(980.0, 460.0, false)
        val NARROW = Frame(360.0, 620.0, true)
    }
}

data class Pt(val x: Double, val y: Double)

data class SankeyBar(val id: String, val x: Double, val y: Double, val w: Double, val h: Double)

/**
 * A band's two ends on the source bar (`a0..a1`) and the target bar (`b0..b1`), in frame x/y for
 * either orientation. Drawn `a0 → cubic → b0 → b1 → cubic → a1 → close`, control points at the
 * main-axis midpoint.
 */
data class SankeyBand(val from: String, val to: String, val a0: Pt, val a1: Pt, val b0: Pt, val b1: Pt)

enum class Anchor { START, MIDDLE, END }

/** [y] is the first line's baseline. */
data class SankeyLabel(val id: String, val x: Double, val y: Double, val anchor: Anchor)

/** The stretch of the house bar a source's band lands on, as shares of the bar's length. */
data class MixStop(val from: String, val start: Double, val end: Double)

data class Placed(
    val bars: List<SankeyBar> = emptyList(),
    val bands: List<SankeyBand> = emptyList(),
    /** Same order as [bars]. */
    val labels: List<SankeyLabel> = emptyList(),
    /** The house bar painted in what flows into it, from its top (left) end. */
    val houseMix: List<MixStop> = emptyList(),
)

object SankeyLayout {
    /** Bar thickness across the main axis. */
    const val BAR = 16.0

    /** Line height of a two-line narrow label (value, then name). */
    const val LINE = 14.0

    /** Half the widest narrow label (12 characters at ~7 px), so one centred on a bar stays inside. */
    const val LABEL_HALF = 44.0

    /** How far a narrow label steps away from its bar to clear the one beside it. */
    private const val ROW_STEP = 2.0 * LINE + 4.0

    private class Slot(val cross: Double, val len: Double, val main: Double)

    /** Where a column sits along the main axis, as a share of it. */
    private fun columnShare(column: Int, vertical: Boolean): Double {
        // Vertical: room above the sources for a second row of labels.
        val shares = if (vertical) doubleArrayOf(0.14, 0.46, 0.78) else doubleArrayOf(0.17, 0.49, 0.79)
        return shares[column.coerceIn(0, 2)]
    }

    fun place(s: Sankey, frame: Frame): Placed {
        val v = frame.vertical
        val mainSize = if (v) frame.h else frame.w
        val crossSize = if (v) frame.w else frame.h
        val margin = if (v) 16.0 else 30.0
        val gap = if (v) 12.0 else 20.0
        val space = crossSize - 2 * margin

        // One scale for every column so equal watts look equal.
        var k = Double.POSITIVE_INFINITY
        for (c in 0..2) {
            val col = s.nodes.filter { it.column == c }
            val sum = col.sumOf { it.size }
            if (col.isEmpty() || sum <= 0.0) continue
            k = minOf(k, (space - gap * (col.size - 1)) / sum)
        }
        if (!k.isFinite() || k <= 0.0) return Placed()

        val slots = HashMap<String, Slot>()
        for (c in 0..2) {
            val col = s.nodes.filter { it.column == c }
            if (col.isEmpty()) continue
            val lens = col.map { maxOf(it.size * k, 3.0) }
            val total = lens.sum() + gap * (col.size - 1)
            var cross = margin + (space - total) / 2
            val main = columnShare(c, v) * mainSize
            col.zip(lens).forEach { (nd, len) ->
                slots[nd.id] = Slot(cross, len, main)
                cross += len + gap
            }
        }

        val bars = mutableListOf<SankeyBar>()
        val labels = mutableListOf<SankeyLabel>()
        for (nd in s.nodes) {
            val sl = slots[nd.id] ?: continue
            val bar = if (v) SankeyBar(nd.id, sl.cross, sl.main, sl.len, BAR) else SankeyBar(nd.id, sl.main, sl.cross, BAR, sl.len)
            bars += bar
            val house = nd.role == SankeyRole.HOUSE
            labels += if (v) {
                // Two lines. Sources and the house read above their bar, sinks below.
                val cx = bar.x + bar.w / 2
                val ly = if (house || nd.column == 0) bar.y - 8 - LINE else bar.y + bar.h + 16
                // Near an edge the label hangs inward rather than being clipped.
                when {
                    cx - LABEL_HALF < 0 -> SankeyLabel(nd.id, bar.x, ly, Anchor.START)
                    cx + LABEL_HALF > frame.w -> SankeyLabel(nd.id, bar.x + bar.w, ly, Anchor.END)
                    else -> SankeyLabel(nd.id, cx, ly, Anchor.MIDDLE)
                }
            } else if (house) {
                SankeyLabel(nd.id, bar.x + bar.w / 2, bar.y - 12, Anchor.MIDDLE)
            } else if (nd.column == 0) {
                SankeyLabel(nd.id, bar.x - 14, bar.y + bar.h / 2, Anchor.END)
            } else {
                SankeyLabel(nd.id, bar.x + bar.w + 14, bar.y + bar.h / 2, Anchor.START)
            }
        }
        if (v) stagger(bars, labels)

        // Bands stack from the top (left) of each bar in link order.
        val leaving = HashMap<String, Double>()
        val entering = HashMap<String, Double>()
        val bands = mutableListOf<SankeyBand>()
        val houseMix = mutableListOf<MixStop>()
        val houseIds = s.nodes.filter { it.role == SankeyRole.HOUSE }.map { it.id }.toSet()
        for (l in s.links) {
            val a = slots[l.from] ?: continue
            val b = slots[l.to] ?: continue
            val t = maxOf(l.watts * k, 1.5)
            val c0 = a.cross + (leaving[l.from] ?: 0.0)
            leaving[l.from] = (leaving[l.from] ?: 0.0) + t
            val c1 = b.cross + (entering[l.to] ?: 0.0)
            entering[l.to] = (entering[l.to] ?: 0.0) + t
            if (l.to in houseIds) {
                fun share(c: Double) = ((c - b.cross) / b.len).coerceIn(0.0, 1.0)
                houseMix += MixStop(l.from, share(c1), share(c1 + t))
            }
            val m0 = a.main + BAR
            val m1 = b.main
            // Horizontal: (main, cross) = (x, y). Vertical swaps them.
            fun pt(m: Double, c: Double) = if (v) Pt(c, m) else Pt(m, c)
            bands += SankeyBand(l.from, l.to, pt(m0, c0), pt(m0, c0 + t), pt(m1, c1), pt(m1, c1 + t))
        }
        return Placed(bars, bands, labels, houseMix)
    }

    /** The x range a narrow label may cover. */
    private fun span(l: SankeyLabel): Pair<Double, Double> = when (l.anchor) {
        Anchor.START -> l.x to l.x + 2 * LABEL_HALF
        Anchor.MIDDLE -> l.x - LABEL_HALF to l.x + LABEL_HALF
        Anchor.END -> l.x - 2 * LABEL_HALF to l.x
    }

    /**
     * Narrow labels side by side would overlap over thin bars: each one that would steps a row
     * further from its bar, so a run of thin bars reads as a zigzag. Three rows fit below a bar and
     * two above (the frame's top margin). Labels and bars share an index.
     */
    private fun stagger(bars: List<SankeyBar>, labels: MutableList<SankeyLabel>) {
        val order = labels.indices.sortedWith(compareBy<Int> { labels[it].y }.thenBy { span(labels[it]).first })
        // Per row of labels (keyed by its first baseline), where each step ends on the x axis.
        val rows = mutableListOf<Pair<Double, DoubleArray>>()
        for (i in order) {
            val y = labels[i].y
            val (from, to) = span(labels[i])
            val above = y < bars[i].y
            var row = rows.indexOfFirst { it.first == y }
            if (row < 0) {
                rows += y to DoubleArray(if (above) 2 else 3) { Double.NEGATIVE_INFINITY }
                row = rows.lastIndex
            }
            val ends = rows[row].second
            // The first step with room, else the one that ends earliest.
            val step = ends.indexOfFirst { from >= it }.takeIf { it >= 0 } ?: ends.indices.minBy { ends[it] }
            ends[step] = to
            val shift = step * ROW_STEP
            labels[i] = labels[i].copy(y = if (above) y - shift else y + shift)
        }
    }
}
