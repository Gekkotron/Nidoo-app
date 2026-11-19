package com.gekkotron.nidoo.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCoreSummaryTest {
    @Test
    fun countsDeviceKindsFromTheSharedCore() {
        assertEquals("13 device kinds from the shared core", sharedCoreSummary())
    }
}
