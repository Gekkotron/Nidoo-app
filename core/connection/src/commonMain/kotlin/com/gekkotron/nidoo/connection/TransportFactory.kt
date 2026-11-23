package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.demo.DemoBroker
import com.gekkotron.nidoo.transport.DirectLink
import com.gekkotron.nidoo.transport.GatewayApi
import com.gekkotron.nidoo.transport.GatewayTransport
import com.gekkotron.nidoo.transport.LocalProbe
import com.gekkotron.nidoo.transport.ManagedTransport
import com.gekkotron.nidoo.transport.TransportSelector
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Builds `ActiveTransport`'s transport for a spec (spec §2; gateway spec §5). The platform supplies the links:
 * [direct] (HiveMQ on Android, CocoaMQTT on iOS), [gatewayApi] (the SDK on OkHttp or Darwin) and [probe] (the LAN check).
 */
class TransportFactory(
    private val scope: CoroutineScope,
    private val networkChanges: Flow<Unit>,
    private val direct: (TransportSpec.Direct, CoroutineScope) -> DirectLink,
    private val gatewayApi: (TransportSpec.Gateway) -> GatewayApi,
    private val probe: (host: String, port: Int) -> LocalProbe,
) {
    fun create(spec: TransportSpec): ManagedTransport = when (spec) {
        TransportSpec.Demo -> DemoBroker(scope)
        is TransportSpec.Direct -> direct(spec)
        is TransportSpec.Gateway -> gateway(spec)
        is TransportSpec.Routed -> TransportSelector(
            spec,
            direct = ::direct,
            gateway = ::gateway,
            probe = probe(spec.direct.host, spec.direct.port),
            networkChanges = networkChanges,
            scope = scope,
        )
    }

    private fun direct(spec: TransportSpec.Direct): ManagedTransport = direct(spec, scope)

    private fun gateway(spec: TransportSpec.Gateway): ManagedTransport = GatewayTransport(gatewayApi(spec), scope)
}
