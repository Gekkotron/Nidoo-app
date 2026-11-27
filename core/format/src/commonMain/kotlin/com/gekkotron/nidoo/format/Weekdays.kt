package com.gekkotron.nidoo.format

/** The first day of the week for [locale], numbered like the hub: 0 = Sunday ... 6 = Saturday. */
expect fun firstDayOfWeek(locale: FormatLocale): Int

/** The short weekday name for [day] (0 = Sunday ... 6 = Saturday) in [locale], such as "Mon" or "lun.". */
expect fun shortWeekdayName(day: Int, locale: FormatLocale): String
