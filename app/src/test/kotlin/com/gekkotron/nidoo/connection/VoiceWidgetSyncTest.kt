package com.gekkotron.nidoo.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceWidgetSyncTest {
    @Test
    fun `redraws the widgets on every toggle but not for the value read at start`() = runTest {
        val enabled = MutableStateFlow<Boolean?>(null)
        var updates = 0
        VoiceWidgetSync(enabled, update = { updates++ }, scope = backgroundScope).start()
        runCurrent()
        enabled.value = false // DataStore's first real read: the widgets already drew from it.
        runCurrent()
        assertEquals(0, updates)
        enabled.value = true
        runCurrent()
        assertEquals(1, updates)
        enabled.value = true
        runCurrent()
        assertEquals(1, updates)
        enabled.value = false
        runCurrent()
        assertEquals(2, updates)
    }
}
