package com.gekkotron.nidoo.shared

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoHouseSummaryTest {
    /** 44 is the demo house's entity list pinned by DemoBrokerTest's `allIds`. */
    @Test
    fun countsDemoHouseDevices() = runTest {
        assertEquals("Demo house: 44 entities", demoHouseSummary(this))
    }
}
