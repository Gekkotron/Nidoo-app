package com.gekkotron.nidoo.transport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicFilterTest {
    @Test
    fun `exact match`() {
        assertTrue(topicMatches("a/b", "a/b"))
        assertFalse(topicMatches("a/b", "a/c"))
        assertFalse(topicMatches("a/b", "a/b/c"))
    }

    @Test
    fun `single-level wildcard`() {
        assertTrue(topicMatches("a/+/c", "a/b/c"))
        assertFalse(topicMatches("a/+", "a/b/c"))
        assertTrue(topicMatches("fcm-push/tokens/+", "fcm-push/tokens/abc"))
    }

    @Test
    fun `multi-level wildcard includes the parent level`() {
        assertTrue(topicMatches("homeassistant/#", "homeassistant/light/x/config"))
        assertTrue(topicMatches("a/#", "a"))
        assertTrue(topicMatches("#", "anything/at/all"))
    }

    @Test
    fun `dollar topics are not matched by leading wildcards`() {
        assertFalse(topicMatches("#", "\$SYS/broker/uptime"))
        assertFalse(topicMatches("+/broker", "\$SYS/broker"))
        assertTrue(topicMatches("\$SYS/#", "\$SYS/broker/uptime"))
    }

    @Test
    fun `publish topics cannot contain wildcards`() {
        assertTrue(isValidPublishTopic("nidoo/ui/0x00_light"))
        assertFalse(isValidPublishTopic("nidoo/ui/a+b"))
        assertFalse(isValidPublishTopic("nidoo/ui/#"))
        assertFalse(isValidPublishTopic(""))
    }

    @Test
    fun subscribeFiltersFollowMqtt311() {
        for (ok in listOf("#", "+", "a/#", "a/+/b", "+/+", "homeassistant/light/+/config", "\$SYS/#", "a//b")) assertTrue(isValidSubscribeFilter(ok), ok)
        for (bad in listOf("", "a/#/b", "a#", "a/b#", "x+/y", "a/+b", "nul\u0000", "x".repeat(65_536))) assertFalse(isValidSubscribeFilter(bad), bad.take(20))
    }
}
