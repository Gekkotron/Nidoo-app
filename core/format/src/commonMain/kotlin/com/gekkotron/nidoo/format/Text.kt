package com.gekkotron.nidoo.format

/** Canonical decomposition (NFD) of [text]. */
expect fun nfd(text: String): String

private val MARKS = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
)

/** [text] without its accents and other combining marks. */
fun stripMarks(text: String): String = nfd(text).filterNot { it.category in MARKS }
