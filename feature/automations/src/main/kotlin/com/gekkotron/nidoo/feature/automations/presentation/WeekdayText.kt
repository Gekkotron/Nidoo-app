package com.gekkotron.nidoo.feature.automations.presentation

import java.time.DayOfWeek

/** [days] 0=Sunday..6. Ordered from [firstDay]; a run of 3+ consecutive days is "first–last", others comma-separated. */
fun weekdayText(days: List<Int>, firstDay: DayOfWeek, shortName: (DayOfWeek) -> String): String {
    val positions = days.distinct()
        .map { d -> (DayOfWeek.of(if (d == 0) 7 else d).value - firstDay.value + 7) % 7 }
        .sorted()
    fun name(pos: Int) = shortName(DayOfWeek.of((pos + firstDay.value - 1) % 7 + 1))
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
