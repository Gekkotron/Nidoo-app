package com.gekkotron.nidoo.format

/** Formats an instant with a date pattern (`d MMM`, `HH:mm`...); a null [timeZoneId] means the system zone. */
expect fun formatEpochSeconds(
    seconds: Long,
    pattern: String,
    locale: FormatLocale,
    timeZoneId: String? = null,
): String

/** The hour of day (0..23) of an instant in [timeZoneId], or the system zone when null. */
expect fun localHour(epochSeconds: Long, timeZoneId: String? = null): Int
