package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.demo.DemoBroker
import com.gekkotron.nidoo.transport.DirectMqttTransport
import com.gekkotron.nidoo.transport.GatewayTransport
import com.gekkotron.nidoo.transport.Route
import com.gekkotron.nidoo.transport.TransportSelector
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient
import org.junit.Test
import kotlin.test.assertIs

class TransportFactoryTest {
    private val factory = TransportFactory(TestScope(), emptyFlow(), OkHttpClient())
    private val direct = TransportSpec.Direct("192.168.1.91", 1883, tls = false, ws = false, username = null, password = null, clientId = "c")
    private val gateway = TransportSpec.Gateway("https://gw.example", ByteArray(32), null, null, null)

    @Test
    fun `each spec builds its transport`() {
        assertIs<DemoBroker>(factory.create(TransportSpec.Demo))
        assertIs<DirectMqttTransport>(factory.create(direct))
        assertIs<GatewayTransport>(factory.create(gateway))
        assertIs<TransportSelector>(factory.create(TransportSpec.Routed(direct, gateway, Route.AUTO)))
    }
}
