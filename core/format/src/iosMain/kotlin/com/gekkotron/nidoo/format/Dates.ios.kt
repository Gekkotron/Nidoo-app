package com.gekkotron.nidoo.format

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

private fun zone(timeZoneId: String?): NSTimeZone =
    timeZoneId?.let { NSTimeZone.timeZoneWithName(it) } ?: NSTimeZone.localTimeZone

private fun date(seconds: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(seconds.toDouble())

actual fun formatEpochSeconds(
    seconds: Long,
    pattern: String,
    locale: FormatLocale,
    timeZoneId: String?,
): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = pattern
    formatter.locale = locale.toNSLocale()
    formatter.timeZone = zone(timeZoneId)
    return formatter.stringFromDate(date(seconds))
}

actual fun localHour(epochSeconds: Long, timeZoneId: String?): Int {
    val calendar = checkNotNull(NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian))
    calendar.timeZone = zone(timeZoneId)
    return calendar.components(NSCalendarUnitHour, fromDate = date(epochSeconds)).hour.toInt()
}
