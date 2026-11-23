package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.GatewayConfig
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.RoutePreference
import com.gekkotron.nidoo.transport.GatewayDownloader
import com.gekkotron.nidoo.transport.LocalProbe
import com.gekkotron.nidoo.transport.RawDownload
import com.gekkotron.nidoo.transport.RawPost
import com.gekkotron.nidoo.transport.TransportSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object NoDownloader : GatewayDownloader {
    override suspend fun download(url: String, timeoutSeconds: Int, headers: Map<String, String>): RawDownload = error("not called")
    override suspend fun post(url: String, json: String, timeoutSeconds: Int): RawPost = error("not called")
}

class HttpRouteRuleTest {
    private val withGateway = ConnectionProfile.Direct(
        LocalBroker("192.168.1.91", port = 1884),
        gateway = GatewayConfig("https://gw.example", ByteArray(32), "192.168.1.91"),
    )
    private val probed = mutableListOf<Pair<String, Int>>()
    private val gateways = mutableListOf<TransportSpec.Gateway>()

    private fun route(profile: ConnectionProfile?, preference: RoutePreference) = httpRouteFor(
        profile,
        preference,
        local = { host, port -> probed += host to port; LocalProbe { true } },
        gateway = { gateways += it; NoDownloader },
    )

    @Test
    fun demoHasNoRoute() {
        val r = route(ConnectionProfile.Demo, RoutePreference.AUTO)
        assertNull(r.local)
        assertNull(r.gateway)
        assertNull(route(null, RoutePreference.AUTO).local)
    }

    @Test
    fun autoHasProbeAndGateway() {
        val r = route(withGateway, RoutePreference.AUTO)
        assertNotNull(r.local)
        assertNotNull(r.gateway)
        assertEquals(listOf("192.168.1.91" to 1884), probed)
        assertEquals("https://gw.example", gateways.single().url)
    }

    @Test
    fun directRouteSkipsTheGateway() {
        val r = route(withGateway, RoutePreference.DIRECT)
        assertNotNull(r.local)
        assertNull(r.gateway)
    }

    @Test
    fun gatewayRouteSkipsTheProbe() {
        val r = route(withGateway, RoutePreference.GATEWAY)
        assertNull(r.local)
        assertNotNull(r.gateway)
    }
}
