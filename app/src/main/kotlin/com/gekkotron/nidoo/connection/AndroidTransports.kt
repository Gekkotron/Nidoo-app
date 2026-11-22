package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.transport.AthenaGatewayApi
import com.gekkotron.nidoo.transport.DirectMqttTransport
import com.gekkotron.nidoo.transport.TcpProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/** The common [TransportFactory] with Android's links: HiveMQ to the broker, the gateway SDK on OkHttp, a socket probe. */
fun androidTransportFactory(scope: CoroutineScope, networkChanges: Flow<Unit>, http: OkHttpClient): TransportFactory =
    TransportFactory(
        scope,
        networkChanges,
        direct = ::DirectMqttTransport,
        gatewayApi = { AthenaGatewayApi(it, http) },
        probe = { host, port -> TcpProbe(host, port) },
    )

/** The onboarding connection test with Android's links. */
fun androidConnectionTester(clientId: suspend () -> String, http: OkHttpClient): DirectConnectionTester =
    DirectConnectionTester(clientId = clientId, direct = ::DirectMqttTransport, gatewayApi = { AthenaGatewayApi(it, http) })
