package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.demo.DemoBroker
import com.gekkotron.nidoo.transport.AthenaGatewayApi
import com.gekkotron.nidoo.transport.DirectMqttTransport
import com.gekkotron.nidoo.transport.GatewayTransport
import com.gekkotron.nidoo.transport.ManagedTransport
import com.gekkotron.nidoo.transport.TcpProbe
import com.gekkotron.nidoo.transport.TransportSelector
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/** Builds `ActiveTransport`'s transport for a spec (spec §2; gateway spec §5). */
class TransportFactory(
    private val scope: CoroutineScope,
    private val networkChanges: Flow<Unit>,
    private val http: OkHttpClient,
) {
    fun create(spec: TransportSpec): ManagedTransport = when (spec) {
        TransportSpec.Demo -> DemoBroker(scope)
        is TransportSpec.Direct -> direct(spec)
        is TransportSpec.Gateway -> gateway(spec)
        is TransportSpec.Routed -> TransportSelector(
            spec,
            direct = ::direct,
            gateway = ::gateway,
            probe = TcpProbe(spec.direct.host, spec.direct.port),
            networkChanges = networkChanges,
            scope = scope,
        )
    }

    private fun direct(spec: TransportSpec.Direct): ManagedTransport = DirectMqttTransport(spec, scope)

    private fun gateway(spec: TransportSpec.Gateway): ManagedTransport = GatewayTransport(AthenaGatewayApi(spec, http), scope)
}
