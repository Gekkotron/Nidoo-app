package com.gekkotron.nidoo.format

/** Formats [value] with the locale's separators, between [minFraction] and [maxFraction] fraction digits (half-even). */
expect fun formatNumber(
    value: Double,
    locale: FormatLocale,
    minFraction: Int,
    maxFraction: Int,
    grouping: Boolean = true,
): String
