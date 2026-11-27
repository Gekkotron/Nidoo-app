package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@Composable
actual fun is24HourClock(): Boolean = uses24HourClock(NSLocale.currentLocale)

/** The "j" template expands to the region's hour format: it holds an "a" (AM/PM marker) only on a 12-hour clock. */
internal fun uses24HourClock(locale: NSLocale): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, locale)?.contains('a') != true
