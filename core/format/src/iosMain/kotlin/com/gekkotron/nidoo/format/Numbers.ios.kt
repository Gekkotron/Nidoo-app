package com.gekkotron.nidoo.format

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven

actual fun formatNumber(
    value: Double,
    locale: FormatLocale,
    minFraction: Int,
    maxFraction: Int,
    grouping: Boolean,
): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.locale = locale.toNSLocale()
    formatter.minimumFractionDigits = minFraction.toULong()
    formatter.maximumFractionDigits = maxFraction.toULong()
    formatter.usesGroupingSeparator = grouping
    formatter.roundingMode = NSNumberFormatterRoundHalfEven
    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}
