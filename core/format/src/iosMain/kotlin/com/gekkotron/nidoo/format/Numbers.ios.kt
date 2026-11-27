package com.gekkotron.nidoo.format

import platform.Foundation.NSNumber

actual fun formatNumber(
    value: Double,
    locale: FormatLocale,
    minFraction: Int,
    maxFraction: Int,
    grouping: Boolean,
): String {
    val formatter = numberFormatter(locale, minFraction, maxFraction, grouping)
    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}
