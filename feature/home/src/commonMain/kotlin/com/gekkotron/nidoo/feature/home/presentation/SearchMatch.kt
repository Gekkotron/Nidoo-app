package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.format.stripMarks

/** Home search (case- and accent-insensitive; every word of the query must appear in one of the fields). */
object SearchMatch {
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String = stripMarks(text).lowercase()

    fun matches(query: String, vararg fields: String?): Boolean {
        val words = normalize(query).split(spaces).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }
}
