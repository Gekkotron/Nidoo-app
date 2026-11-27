package com.gekkotron.nidoo.format

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter

actual fun firstDayOfWeek(locale: FormatLocale): Int {
    val calendar = checkNotNull(NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian))
    calendar.locale = locale.toNSLocale()
    return (calendar.firstWeekday - 1u).toInt()
}

actual fun shortWeekdayName(day: Int, locale: FormatLocale): String {
    val formatter = NSDateFormatter()
    formatter.locale = locale.toNSLocale()
    return formatter.shortWeekdaySymbols[day] as String
}
