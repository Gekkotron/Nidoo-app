package com.gekkotron.nidoo.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun zone(timeZoneId: String?): ZoneId = timeZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()

actual fun formatEpochSeconds(
    seconds: Long,
    pattern: String,
    locale: FormatLocale,
    timeZoneId: String?,
): String = DateTimeFormatter.ofPattern(pattern, locale)
    .withZone(zone(timeZoneId))
    .format(Instant.ofEpochSecond(seconds))

actual fun localHour(epochSeconds: Long, timeZoneId: String?): Int =
    Instant.ofEpochSecond(epochSeconds).atZone(zone(timeZoneId)).hour
