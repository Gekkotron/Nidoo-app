package com.gekkotron.nidoo.format

import java.text.NumberFormat

actual fun formatNumber(
    value: Double,
    locale: FormatLocale,
    minFraction: Int,
    maxFraction: Int,
    grouping: Boolean,
): String = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = minFraction
    maximumFractionDigits = maxFraction
    isGroupingUsed = grouping
}.format(value)
