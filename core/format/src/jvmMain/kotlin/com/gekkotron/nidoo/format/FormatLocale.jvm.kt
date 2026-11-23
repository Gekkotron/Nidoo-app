package com.gekkotron.nidoo.format

import java.util.Locale

actual typealias FormatLocale = Locale

actual fun systemLocale(): FormatLocale = Locale.getDefault()

actual fun formatLocale(languageTag: String): FormatLocale = Locale.forLanguageTag(languageTag)
