package com.gekkotron.oikos.source

import com.gekkotron.oikos.dashboards.DashboardsSink
import com.gekkotron.oikos.model.Diagnostic
import com.gekkotron.oikos.transport.LinkState
import com.gekkotron.oikos.transport.TransportMode
import com.gekkotron.oikos.transport.testing.FakeMqttTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MqttDiscoverySourceDashboardsTest {
    private class RecordingDashboards : DashboardsSink {
        val retained = mutableListOf<Pair<String, String?>>()
        var linkUps = 0
        var clears = 0
        var replayed = 0
        override fun onRetained(id: String, payload: String?) { retained += id to payload }
        override fun markReplayed() { replayed++ }
        override fun markLinkUp() { linkUps++ }
        override fun clear() { clears++ }
    }

    private class Harness(
        val transport: FakeMqttTransport,
        val source: MqttDiscoverySource,
        val diagnostics: MutableList<Diagnostic>,
    )

    private fun TestScope.start(
        sink: DashboardsSink?,
        transport: FakeMqttTransport = FakeMqttTransport(),
    ): Harness {
        val diagnostics = mutableListOf<Diagnostic>()
        val source = MqttDiscoverySource(transport, backgroundScope, diagnostics = { diagnostics += it }, dashboardsSink = sink)
        runCurrent()
        return Harness(transport, source, diagnostics)
    }

    @Test
    fun `retained dashboard topic forwards id and payload`() = runTest {
        val sink = RecordingDashboards()
        val h = start(sink)
        h.transport.deliver("oikos/ui/dashboards/evening", """{"name":"Evening"}""", retain = true)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("evening" to """{"name":"Evening"}"""), sink.retained)
        assertTrue(h.diagnostics.isEmpty())
    }

    @Test
    fun `empty dashboard payload forwards as delete`() = runTest {
        val sink = RecordingDashboards()
        val h = start(sink)
        h.transport.deliver("oikos/ui/dashboards/evening", "", retain = true)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("evening" to null), sink.retained)
    }

    @Test
    fun `dashboard, ambiance, _home and _types leave overrides untouched`() = runTest {
        val sink = RecordingDashboards()
        val h = start(sink)
        h.transport.deliver(
            "homeassistant/light/light_a/config",
            """{"name":"A","unique_id":"light_a","command_topic":"t/set","state_topic":"t/state"}""",
            retain = true,
        )
        val doc = """{"favorite":true}"""
        for (t in listOf("dashboards/evening", "ambiances/soir", "_home", "_types", "light_a")) {
            h.transport.deliver("oikos/ui/$t", doc, retain = true)
        }
        runCurrent()
        val entities = h.source.entities.value
        assertEquals(setOf("light_a"), entities.filterValues { it.ui.favorite }.keys)
        assertFalse(entities.keys.any { "dashboards" in it })
        val keys = h.source.journal().keys
        for (t in listOf("dashboards/evening", "ambiances/soir", "_home", "_types")) assertFalse("oikos/ui/$t" in keys, "oikos/ui/$t leaked into overrides")
        assertTrue("oikos/ui/light_a" in keys)
        assertEquals(3, h.diagnostics.size)
    }

    @Test
    fun `a nested dashboards topic is ignored`() = runTest {
        val sink = RecordingDashboards()
        val h = start(sink)
        h.transport.deliver("oikos/ui/dashboards/a/b", """{"favorite":true}""", retain = true)
        runCurrent()
        assertTrue(sink.retained.isEmpty())
        assertTrue(h.source.entities.value.isEmpty())
        assertFalse("oikos/ui/dashboards/a/b" in h.source.journal().keys)
        assertEquals(listOf("oikos/ui/dashboards/a/b: not an override, ignored"), h.diagnostics.map { it.message })
    }

    @Test
    fun `an ignored document is reported once`() = runTest {
        val h = start(RecordingDashboards())
        h.transport.deliver("oikos/ui/_home", """{"a":1}""", retain = true)
        h.transport.deliver("oikos/ui/_home", """{"a":2}""", retain = true)
        runCurrent()
        assertEquals(listOf("oikos/ui/_home: not an override, ignored"), h.diagnostics.map { it.message })
    }

    @Test
    fun `a plain override still applies`() = runTest {
        val h = start(RecordingDashboards())
        h.transport.deliver(
            "homeassistant/light/light_a/config",
            """{"name":"A","unique_id":"light_a","command_topic":"t/set","state_topic":"t/state"}""",
            retain = true,
        )
        h.transport.deliver("oikos/ui/light_a", """{"favorite":true}""", retain = true)
        runCurrent()
        assertTrue(h.source.entities.value["light_a"]!!.ui.favorite)
    }

    @Test
    fun `building the source clears the dashboards sink`() = runTest {
        val sink = RecordingDashboards()
        start(sink)
        assertEquals(1, sink.clears)
    }

    @Test
    fun `link up reaches the dashboards sink`() = runTest {
        val sink = RecordingDashboards()
        val transport = FakeMqttTransport()
        transport.setLink(LinkState.Offline("boot"))
        val h = start(sink, transport)
        assertEquals(0, sink.linkUps)
        h.transport.setLink(LinkState.Connected(TransportMode.DIRECT))
        runCurrent()
        assertEquals(1, sink.linkUps)
    }

    @Test
    fun `restored dashboard messages are not forwarded`() = runTest {
        val sink = RecordingDashboards()
        val h = start(sink)
        h.source.restore(mapOf("oikos/ui/dashboards/x" to """{"name":"X"}"""))
        runCurrent()
        assertTrue(sink.retained.isEmpty())
    }

    @Test
    fun `the dashboards sink is told when the replay went quiet`() = runTest {
        val sink = RecordingDashboards()
        val transport = FakeMqttTransport()
        transport.setLink(LinkState.Offline("boot"))
        val h = start(sink, transport)
        h.transport.setLink(LinkState.Connected(TransportMode.DIRECT))
        runCurrent()
        h.transport.deliver("oikos/ui/dashboards/evening", """{"name":"Evening"}""", retain = true)
        runCurrent()
        assertEquals(0, sink.replayed)
        advanceTimeBy(MqttDiscoverySource.SETTLE_QUIET.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(1, sink.replayed)
        assertTrue(h.source.hasSettled.value)
    }
}
