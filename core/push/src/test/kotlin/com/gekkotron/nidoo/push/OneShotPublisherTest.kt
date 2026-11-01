package com.gekkotron.nidoo.push

import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.LocalProbe
import com.gekkotron.nidoo.transport.PublishFailed
import com.gekkotron.nidoo.transport.Route
import com.gekkotron.nidoo.transport.TransportMode
import com.gekkotron.nidoo.transport.TransportSpec
import com.gekkotron.nidoo.transport.testing.FakeGatewayApi
import com.gekkotron.nidoo.transport.testing.FakeManagedTransport
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotPublisherTest {
    private val direct = TransportSpec.Direct("192.168.1.91", 1883, false, false, "nidoo", "pw", "nidoo-abc-push")
    private val gateway = TransportSpec.Gateway("https://gw.example", ByteArray(32), "192.168.1.91", "nidoo", "pw")
    private fun routed(route: Route) = TransportSpec.Routed(direct, gateway, route)

    private val live = FakeMqttTransport().apply { setLink(LinkState.Offline(LinkState.Offline.PAUSED)) }
    private val gatewayApi = FakeGatewayApi()
    private val directs = mutableListOf<FakeManagedTransport>()
    private var probeAnswer = true
    private var probes = 0
    private var directLink: LinkState = LinkState.Connected(TransportMode.DIRECT)
    private val payload = """{"token":"t"}""".encodeToByteArray()

    private fun publisher(spec: TransportSpec?) = OneShotPublisher(
        live = live,
        specs = { spec },
        probe = { LocalProbe { probes++; probeAnswer } },
        directTransport = { s, _ -> FakeManagedTransport(s).also { it.fake.setLink(directLink); directs += it } },
        gatewayApi = { gatewayApi },
    )

    private suspend fun OneShotPublisher.send(retain: Boolean = true) = publish("nidoo/push/tokens/abc", payload, retain)

    @Test
    fun `a connected live transport is used first and nothing else is touched`(): Unit = runTest {
        live.setLink(LinkState.Connected(TransportMode.DIRECT))
        assertTrue(publisher(routed(Route.AUTO)).send().isSuccess)
        assertEquals(listOf("nidoo/push/tokens/abc" to """{"token":"t"}"""), live.publishedStrings())
        assertEquals(true, live.published.single().retained)
        assertEquals(0, probes)
        assertTrue(directs.isEmpty() && gatewayApi.published.isEmpty())
    }

    @Test
    fun `a failing live publish falls through to the stored route`(): Unit = runTest {
        live.setLink(LinkState.Connected(TransportMode.DIRECT))
        live.failPublishes = true
        probeAnswer = false
        assertTrue(publisher(routed(Route.AUTO)).send().isSuccess)
        assertEquals(1, gatewayApi.published.size)
    }

    @Test
    fun `auto with a reachable broker publishes through a throwaway direct transport and closes it`(): Unit = runTest {
        assertTrue(publisher(routed(Route.AUTO)).send(retain = true).isSuccess)
        val transport = directs.single()
        assertTrue(transport.started)
        assertTrue(transport.fake.published.single().let { it.topic == "nidoo/push/tokens/abc" })
        assertTrue(transport.closed)
        assertTrue(gatewayApi.published.isEmpty())
    }

    @Test
    fun `auto with an unreachable broker goes to the gateway without starting a direct transport`(): Unit = runTest {
        probeAnswer = false
        assertTrue(publisher(routed(Route.AUTO)).send().isSuccess)
        assertTrue(directs.isEmpty())
        val sent = gatewayApi.published.single()
        assertEquals("nidoo/push/tokens/abc", sent.topic)
        assertEquals(true, sent.retained)
    }

    @Test
    fun `auto falls back to the gateway when the direct broker never answers within 10 s`(): Unit = runTest {
        directLink = LinkState.Connecting
        assertTrue(publisher(routed(Route.AUTO)).send().isSuccess)
        assertEquals(10_000, testScheduler.currentTime)
        assertTrue(directs.single().closed)
        assertEquals(1, gatewayApi.published.size)
    }

    @Test
    fun `auto falls back to the gateway at once when the broker rejects the credentials`(): Unit = runTest {
        directLink = LinkState.Offline(LinkState.Offline.REJECTED)
        assertTrue(publisher(routed(Route.AUTO)).send().isSuccess)
        assertEquals(0, testScheduler.currentTime)
        assertEquals(1, gatewayApi.published.size)
    }

    @Test
    fun `direct-only never uses the gateway, even when the direct publish fails`(): Unit = runTest {
        directLink = LinkState.Connecting
        val result = publisher(routed(Route.DIRECT)).send()
        assertTrue(result.isFailure)
        assertIs<PublishFailed>(result.exceptionOrNull())
        assertTrue(gatewayApi.published.isEmpty())
        assertEquals(0, probes)
    }

    @Test
    fun `gateway-only never probes or starts a direct transport`(): Unit = runTest {
        assertTrue(publisher(routed(Route.GATEWAY)).send().isSuccess)
        assertEquals(0, probes)
        assertTrue(directs.isEmpty())
        assertEquals(1, gatewayApi.published.size)
    }

    @Test
    fun `a plain direct or gateway spec uses its only route`(): Unit = runTest {
        assertTrue(publisher(direct).send().isSuccess)
        assertEquals(1, directs.size)
        assertTrue(publisher(gateway).send().isSuccess)
        assertEquals(1, gatewayApi.published.size)
    }

    @Test
    fun `a failing gateway publish is the result`(): Unit = runTest {
        probeAnswer = false
        gatewayApi.failPublish = "gateway key not accepted"
        val result = publisher(routed(Route.AUTO)).send()
        assertEquals("gateway key not accepted", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the demo and no profile are NotConfigured and publish nothing`(): Unit = runTest {
        for (spec in listOf<TransportSpec?>(TransportSpec.Demo, null)) {
            assertIs<NotConfigured>(publisher(spec).send().exceptionOrNull())
        }
        assertTrue(live.published.isEmpty() && gatewayApi.published.isEmpty() && directs.isEmpty())
    }
}
