package com.gekkotron.nidoo.feature.security.ui

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.designsystem.currentFormatLocale
import com.gekkotron.nidoo.format.FormatLocale
import com.gekkotron.nidoo.format.formatEpochSeconds
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

@Composable
internal actual fun sinceDateText(at: Instant, now: Instant): String = sinceDate(at, now, currentFormatLocale(), null)

/**
 * "08:42" today, "Tue 08:42" within six days, "3 Oct, 08:42" before. The skeletons use `j`, so the locale picks
 * the 12 or 24-hour clock; a null [timeZoneId] means the system zone.
 */
internal fun sinceDate(at: Instant, now: Instant, locale: FormatLocale, timeZoneId: String?): String {
    val calendar = checkNotNull(NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian))
    calendar.timeZone = timeZoneId?.let { NSTimeZone.timeZoneWithName(it) } ?: NSTimeZone.localTimeZone
    val atDay = calendar.startOfDayForDate(NSDate.dateWithTimeIntervalSince1970(at.epochSeconds.toDouble()))
    val today = calendar.startOfDayForDate(NSDate.dateWithTimeIntervalSince1970(now.epochSeconds.toDouble()))
    val daysBack = calendar.components(NSCalendarUnitDay, fromDate = atDay, toDate = today, options = 0u).day
    val skeleton = when {
        daysBack == 0L -> "jm"
        daysBack <= 6L -> "EEEjm"
        else -> "dMMMjm"
    }
    val pattern = NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, NSLocale(locale.identifier)) ?: skeleton
    return formatEpochSeconds(at.epochSeconds, pattern, locale, timeZoneId)
}
