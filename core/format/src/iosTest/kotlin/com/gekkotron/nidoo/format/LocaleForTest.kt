package com.gekkotron.nidoo.format

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocaleForTest {
    @Test fun currentLocaleKeepsItsOverrides() {
        val cur = NSLocale.currentLocale
        assertSame(cur, localeFor(cur.localeIdentifier, cur))
    }

    @Test fun otherLocalesAreBuilt() {
        assertEquals("fr_FR", localeFor("fr_FR", NSLocale(localeIdentifier = "en_US")).localeIdentifier)
    }
}
