package com.gekkotron.nidoo.alerts

import org.junit.Test
import kotlin.test.assertEquals

class PushTopicsTest {
    @Test
    fun `the default template gets the install id`() {
        assertEquals("nidoo/push/tokens/abc-123", PushTopics.tokenTopic(null, "abc-123"))
    }

    @Test
    fun `a profile template overrides the default`() {
        assertEquals("custom/tokens/abc-123", PushTopics.tokenTopic("custom/tokens/{installId}", "abc-123"))
    }

    @Test
    fun `the fixed topics`() {
        assertEquals("nidoo/push/responses", PushTopics.RESPONSES)
        assertEquals("nidoo/push/history", PushTopics.HISTORY)
    }
}
