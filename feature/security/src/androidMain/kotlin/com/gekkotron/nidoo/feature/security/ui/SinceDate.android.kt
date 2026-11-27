package com.gekkotron.nidoo.feature.security.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant
import kotlin.time.Instant as KInstant

@Composable
internal actual fun sinceDateText(at: KInstant, now: KInstant): String = sinceDate(
    at.toJavaInstant(), now.toJavaInstant(), ZoneId.systemDefault(),
    LocalConfiguration.current.locales[0], DateFormat.is24HourFormat(LocalContext.current),
)

/** "08:42" today, "Tue 08:42" within six days, "3 Oct, 08:42" before (locale patterns, last-change spec §4.5). */
internal fun sinceDate(at: Instant, now: Instant, zone: ZoneId, locale: Locale, is24h: Boolean): String {
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = if (is24h) "Hm" else "hm"
    val skeleton = when {
        day == today -> time
        !day.isBefore(today.minusDays(6)) -> "EEE$time"
        else -> "dMMM$time"
    }
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(at.atZone(zone))
}
