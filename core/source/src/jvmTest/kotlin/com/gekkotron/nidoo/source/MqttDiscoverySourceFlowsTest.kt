package com.gekkotron.nidoo.source

import com.gekkotron.nidoo.flows.FlowsSink
import com.gekkotron.nidoo.model.Diagnostic
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MqttDiscoverySourceFlowsTest {
    private class RecordingSink : FlowsSink {
        val retained = mutableListOf<Pair<String, String?>>()
        var linkUps = 0
        var clears = 0
        override fun onRetained(id: String, payload: String?) { retained += id to payload }
        override fun markLinkUp() { linkUps++ }
        override fun clear() { clears++ }
    }

    private class Harness(
        val transport: FakeMqttTransport,
        val source: MqttDiscoverySource,
        val diagnostics: MutableList<Diagnostic>,
    )

    private fun TestScope.start(
        sink: FlowsSink?,
        transport: FakeMqttTransport = FakeMqttTransport(),
    ): Harness {
        val diagnostics = mutableListOf<Diagnostic>()
        val source = MqttDiscoverySource(transport, backgroundScope, diagnostics = { diagnostics += it }, flowsSink = sink)
        runCurrent()
        return Harness(transport, source, diagnostics)
    }

    @Test
    fun `retained flow topic forwards id and payload`() = runTest {
        val sink = RecordingSink()
        val h = start(sink)
        h.transport.deliver("nidoo/automation/abc", """{"id":"abc"}""", retain = true)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("abc" to """{"id":"abc"}"""), sink.retained)
        assertTrue(h.diagnostics.isEmpty())
    }

    @Test
    fun `empty payload forwards as delete`() = runTest {
        val sink = RecordingSink()
        val h = start(sink)
        h.transport.deliver("nidoo/automation/abc", "", retain = true)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("abc" to null), sink.retained)
    }

    @Test
    fun `deeper sub-topic is not forwarded`() = runTest {
        val sink = RecordingSink()
        val h = start(sink)
        h.transport.deliver("nidoo/automation/abc/state", "on", retain = true)
        h.transport.deliver("nidoo/automation/", "x", retain = true)
        runCurrent()
        assertTrue(sink.retained.isEmpty())
    }

    @Test
    fun `null sink is a no-op and never reaches the store`() = runTest {
        val h = start(null)
        h.transport.deliver("nidoo/automation/abc", """{"id":"abc"}""", retain = true)
        runCurrent()
        assertTrue(h.source.entities.value.isEmpty())
        assertTrue(h.diagnostics.isEmpty())
        assertTrue(h.source.journal().isEmpty())
    }

    @Test
    fun `restored automation message is not forwarded`() = runTest {
        val sink = RecordingSink()
        val h = start(sink)
        h.source.restore(mapOf("nidoo/automation/abc" to """{"id":"abc"}"""))
        runCurrent()
        assertTrue(sink.retained.isEmpty())
    }

    @Test
    fun `clear is called once at construction`() = runTest {
        val sink = RecordingSink()
        start(sink)
        assertEquals(1, sink.clears)
    }

    @Test
    fun `markLinkUp fires on each transition into Connected`() = runTest {
        val sink = RecordingSink()
        val transport = FakeMqttTransport()
        transport.setLink(LinkState.Offline("boot"))
        val h = start(sink, transport)
        assertEquals(0, sink.linkUps)
        h.transport.setLink(LinkState.Connected(TransportMode.DIRECT))
        runCurrent()
        assertEquals(1, sink.linkUps)
        h.transport.setLink(LinkState.Offline("drop"))
        runCurrent()
        assertEquals(1, sink.linkUps)
        h.transport.setLink(LinkState.Connected(TransportMode.DIRECT))
        runCurrent()
        assertEquals(2, sink.linkUps)
        assertEquals(1, sink.clears)
    }
}
