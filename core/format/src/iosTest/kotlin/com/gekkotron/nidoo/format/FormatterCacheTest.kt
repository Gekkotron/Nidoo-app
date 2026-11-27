package com.gekkotron.nidoo.format

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class FormatterCacheTest {
    private val fr = formatLocale("fr-FR")

    @Test
    fun sameKeySameFormatter() {
        assertSame(numberFormatter(fr, 1, 1, true), numberFormatter(fr, 1, 1, true))
        assertSame(dateFormatter("HH:mm", fr, "Europe/Paris"), dateFormatter("HH:mm", fr, "Europe/Paris"))
    }

    @Test
    fun differentKeysDifferentFormatters() {
        assertNotSame(numberFormatter(fr, 1, 1, true), numberFormatter(fr, 2, 2, true))
    }

    @Test
    fun cacheIsBounded() {
        val cache = FormatterCache<Int, Any>(limit = 2)
        val first = cache.getOrPut(1) { Any() }
        cache.getOrPut(2) { Any() }
        cache.getOrPut(3) { Any() }
        assertNotSame(first, cache.getOrPut(1) { Any() })
    }
}
