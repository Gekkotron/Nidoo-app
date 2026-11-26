package com.gekkotron.nidoo.format

import kotlin.concurrent.AtomicReference
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

/**
 * A copy-on-write map behind an [AtomicReference]. A race can build a value twice, which is harmless.
 * When the map is full, it restarts with just the new entry.
 */
internal class FormatterCache<K : Any, V : Any>(private val limit: Int = 64) {
    private val entries = AtomicReference<Map<K, V>>(emptyMap())

    fun getOrPut(key: K, create: () -> V): V {
        entries.value[key]?.let { return it }
        val created = create()
        while (true) {
            val current = entries.value
            current[key]?.let { return it }
            val next = if (current.size >= limit) mapOf(key to created) else current + (key to created)
            if (entries.compareAndSet(current, next)) return created
        }
    }
}

private data class NumberKey(val locale: String, val minFraction: Int, val maxFraction: Int, val grouping: Boolean)

private data class DateKey(val pattern: String, val locale: String, val timeZoneName: String)

private val numberFormatters = FormatterCache<NumberKey, NSNumberFormatter>()
private val dateFormatters = FormatterCache<DateKey, NSDateFormatter>()

internal fun numberFormatter(locale: FormatLocale, minFraction: Int, maxFraction: Int, grouping: Boolean): NSNumberFormatter =
    numberFormatters.getOrPut(NumberKey(locale.identifier, minFraction, maxFraction, grouping)) {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            this.locale = locale.toNSLocale()
            minimumFractionDigits = minFraction.toULong()
            maximumFractionDigits = maxFraction.toULong()
            usesGroupingSeparator = grouping
            roundingMode = NSNumberFormatterRoundHalfEven
        }
    }

internal fun dateFormatter(pattern: String, locale: FormatLocale, timeZoneId: String?): NSDateFormatter {
    // A null id follows the device's zone, so the key uses the resolved zone's name.
    val zone = timeZoneId?.let { NSTimeZone.timeZoneWithName(it) } ?: NSTimeZone.localTimeZone
    return dateFormatters.getOrPut(DateKey(pattern, locale.identifier, zone.name)) {
        NSDateFormatter().apply {
            dateFormat = pattern
            this.locale = locale.toNSLocale()
            timeZone = zone
        }
    }
}
