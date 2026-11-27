package com.gekkotron.nidoo.format

import platform.Foundation.NSLocale
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual class FormatLocale(val identifier: String) {
    override fun equals(other: Any?): Boolean = other is FormatLocale && other.identifier == identifier

    override fun hashCode(): Int = identifier.hashCode()

    override fun toString(): String = identifier
}

actual fun systemLocale(): FormatLocale = FormatLocale(NSLocale.currentLocale.localeIdentifier)

actual fun formatLocale(languageTag: String): FormatLocale =
    FormatLocale(NSLocale.canonicalLocaleIdentifierFromString(languageTag))

/**
 * The Foundation locale for this one. For the current locale this is `NSLocale.currentLocale` itself, because only
 * it carries the user's overrides such as the 12/24-hour clock switch.
 */
fun FormatLocale.toNSLocale(): NSLocale = localeFor(identifier, NSLocale.currentLocale)

internal fun localeFor(identifier: String, current: NSLocale): NSLocale =
    if (current.localeIdentifier == identifier) current else NSLocale(localeIdentifier = identifier)
