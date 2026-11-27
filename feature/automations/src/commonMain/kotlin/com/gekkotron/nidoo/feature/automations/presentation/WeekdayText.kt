package com.gekkotron.nidoo.feature.automations.presentation

/** [days] 0=Sunday..6. Ordered from [firstDay]; a run of 3+ consecutive days is "first–last", others comma-separated. */
fun weekdayText(days: List<Int>, firstDay: Int, shortName: (Int) -> String): String {
    val positions = days.distinct().map { d -> (d - firstDay + 7) % 7 }.sorted()
    fun name(pos: Int) = shortName((pos + firstDay) % 7)
    val parts = mutableListOf<String>()
    var i = 0
    while (i < positions.size) {
        var j = i
        while (j + 1 < positions.size && positions[j + 1] == positions[j] + 1) j++
        if (j - i + 1 >= 3) {
            parts += "${name(positions[i])}–${name(positions[j])}"
        } else {
            for (k in i..j) parts += name(positions[k])
        }
        i = j + 1
    }
    return parts.joinToString(", ")
}
