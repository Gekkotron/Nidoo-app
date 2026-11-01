package com.gekkotron.oikos.dashboards

/** Port of the hub's `layout()` (oikos-core `dashboard/layout.rs`): where each widget sits on the 6-column grid. */
object DashboardLayout {
    const val GRID_COLUMNS = 6

    private data class Rect(val col: Int, val row: Int, val w: Int, val h: Int) {
        fun overlaps(o: Rect) = col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h
        fun fits() = col + w <= GRID_COLUMNS
    }

    private fun rect(at: Cell, span: WidgetSpan) = Rect(at.col, at.row, span.cols, span.rows)

    private fun firstFree(taken: List<Rect>, from: Cell, span: WidgetSpan): Cell {
        val w = minOf(span.cols, GRID_COLUMNS)
        var row = from.row
        var col = from.col
        while (true) {
            while (col + w <= GRID_COLUMNS) {
                val at = Cell(row, col)
                if (taken.none { it.overlaps(rect(at, span)) }) return at
                col++
            }
            row++
            col = 0
        }
    }

    /**
     * The cell of every widget, in list order. Positioned widgets keep their cell unless it runs past the grid
     * or covers an earlier widget; the others flow into the first free cells after the previous one. Rows no
     * widget covers are dropped.
     */
    fun cells(widgets: List<Widget>): List<Cell> {
        val cells = arrayOfNulls<Cell>(widgets.size)
        val taken = ArrayList<Rect>(widgets.size)
        widgets.forEachIndexed { i, w ->
            val at = w.pos ?: return@forEachIndexed
            val r = rect(at, w.span)
            if (r.fits() && taken.none { it.overlaps(r) }) {
                cells[i] = at
                taken += r
            }
        }
        var cursor = Cell(0, 0)
        widgets.forEachIndexed { i, w ->
            if (cells[i] == null) {
                val at = firstFree(taken, cursor, w.span)
                taken += rect(at, w.span)
                cells[i] = at
                cursor = Cell(at.row, at.col + w.span.cols)
            }
        }
        val used = taken.flatMap { it.row until it.row + it.h }.toSet()
        return cells.map { c ->
            val at = c ?: Cell(0, 0)
            Cell(used.count { it < at.row }, at.col) // rows above that some widget covers
        }
    }

    /** The widgets sorted by their laid-out (row, col); stable, so ties keep list order. */
    fun readingOrder(widgets: List<Widget>): List<Widget> {
        val cells = cells(widgets)
        return widgets.indices
            .sortedWith(compareBy({ cells[it].row }, { cells[it].col }))
            .map { widgets[it] }
    }
}
