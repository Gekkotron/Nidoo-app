package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@Composable
actual fun is24HourClock(): Boolean = uses24HourClock(NSLocale.currentLocale)

/**
 * The "j" template expands to the region's hour format. It is a 12-hour clock when the hour field is `h` or `K`
 * (text in single quotes ignored); not by looking for "a", since CLDR's zh_Hant pattern is "Bh時".
 */
internal fun uses24HourClock(locale: NSLocale): Boolean {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, locale) ?: return true
    val fields = pattern.split('\'').filterIndexed { index, _ -> index % 2 == 0 }.joinToString("")
    return fields.none { it == 'h' || it == 'K' }
}
