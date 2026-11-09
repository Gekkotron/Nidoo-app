package com.gekkotron.nidoo.source

import com.gekkotron.nidoo.climate.ClimateDoc
import com.gekkotron.nidoo.climate.ClimateSink
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
class MqttDiscoverySourceClimateTest {
    private class RecordingClimate : ClimateSink {
        val retained = mutableListOf<Triple<String, ClimateDoc, String?>>()
        var clears = 0
        var linkUps = 0
        override fun markLinkUp() { linkUps++ }
        override fun onRetained(zone: String, doc: ClimateDoc, payload: String?) { retained += Triple(zone, doc, payload) }
        override fun clear() { clears++ }
    }

    private class Harness(val transport: FakeMqttTransport, val source: MqttDiscoverySource)

    private fun TestScope.start(sink: ClimateSink?): Harness {
        val transport = FakeMqttTransport()
        val source = MqttDiscoverySource(transport, backgroundScope, climateSink = sink)
        runCurrent()
        return Harness(transport, source)
    }

    private val decision = """{"zone":"living","source":"schedule","reason":"","at":1,"entities":{}}"""

    @Test
    fun routesZoneDocsToSink() = runTest {
        val sink = RecordingClimate()
        val h = start(sink)
        h.transport.deliver("nidoo/climate/zone/living/decision", decision, retain = true)
        h.transport.deliver("nidoo/climate/zone/living/override", "", retain = true)
        runCurrent()
        assertEquals(
            listOf(
                Triple("living", ClimateDoc.DECISION, decision),
                Triple<String, ClimateDoc, String?>("living", ClimateDoc.OVERRIDE, null),
            ),
            sink.retained,
        )
        assertTrue(h.source.entities.value.isEmpty())
    }

    @Test
    fun buildingTheSourceClearsTheSink() = runTest {
        val sink = RecordingClimate()
        start(sink)
        assertEquals(1, sink.clears)
    }

    @Test
    fun linkUpReachesTheSink() = runTest {
        val sink = RecordingClimate()
        val h = start(sink)
        h.transport.setLink(LinkState.Connected(TransportMode.DIRECT))
        runCurrent()
        assertEquals(1, sink.linkUps)
    }

    @Test
    fun ignoresRestoredClimate() = runTest {
        val sink = RecordingClimate()
        val h = start(sink)
        h.source.restore(mapOf("nidoo/climate/zone/living/decision" to decision))
        runCurrent()
        assertTrue(sink.retained.isEmpty())
    }

    @Test
    fun holdCommandIsNotAnEntity() = runTest {
        val sink = RecordingClimate()
        val h = start(sink)
        h.transport.deliver("nidoo/climate/zone/living/hold/set", """{"setpoint":22.0,"minutes":60}""")
        h.transport.deliver("nidoo/climate/house", """{"frost":7.0}""", retain = true)
        h.transport.deliver("nidoo/climate/zone/living/proposal", """{"id":"p"}""", retain = true)
        runCurrent()
        assertTrue(sink.retained.isEmpty())
        assertTrue(h.source.entities.value.isEmpty())
        assertTrue(h.source.journal().isEmpty())
    }

    @Test
    fun acDiscoveryUnderTheClimatePrefixStillWorks() = runTest {
        val sink = RecordingClimate()
        val h = start(sink)
        h.transport.deliver(
            "nidoo/climate/daikin_1/climate/config",
            """{"name":"AC","unique_id":"daikin_1","mode_command_topic":"d/mode/set","mode_state_topic":"d/mode","modes":["off","heat"]}""",
            retain = true,
        )
        runCurrent()
        assertTrue(sink.retained.isEmpty())
        assertEquals(1, h.source.entities.value.size)
    }
}
