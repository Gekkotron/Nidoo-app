package com.gekkotron.nidoo.format

/** A locale for number and date formatting (java.util.Locale on the JVM, a locale identifier on iOS). */
expect class FormatLocale

/** The device's current locale. */
expect fun systemLocale(): FormatLocale

/** The locale for a BCP 47 language tag such as `fr-FR`. */
expect fun formatLocale(languageTag: String): FormatLocale
