package com.gekkotron.nidoo.history

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

internal actual fun parisHourOfDay(epochSeconds: Long): Double {
    val calendar = checkNotNull(NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian))
    calendar.timeZone = checkNotNull(NSTimeZone.timeZoneWithName("Europe/Paris"))
    val parts = calendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))
    return parts.hour + parts.minute / 60.0
}
