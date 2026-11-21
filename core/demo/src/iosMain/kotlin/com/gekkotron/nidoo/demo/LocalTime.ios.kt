package com.gekkotron.nidoo.demo

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun localHourMinute(epochSeconds: Long): String = NSDateFormatter().apply {
    dateFormat = "HH:mm"
    locale = NSLocale(localeIdentifier = "en_US_POSIX") // 24-hour digits whatever the user's 12/24 h setting
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))
