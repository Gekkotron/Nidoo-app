package com.gekkotron.nidoo.format

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields

actual fun firstDayOfWeek(locale: FormatLocale): Int = WeekFields.of(locale).firstDayOfWeek.value % 7

actual fun shortWeekdayName(day: Int, locale: FormatLocale): String =
    DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(TextStyle.SHORT, locale)
