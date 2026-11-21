package com.gekkotron.nidoo.transport

import io.github.gekkotron.athena.gateway.client.AthenaGatewayClient
import io.ktor.client.engine.darwin.Darwin

/**
 * The gateway link on iOS: the SDK's own Darwin client, which never follows redirects (so https can't be
 * downgraded). Unlike the JVM factory there is no 10 s publish call timeout: the SDK's iOS factory takes none.
 */
fun AthenaGatewayApi(spec: TransportSpec.Gateway): AthenaGatewayApi =
    AthenaGatewayApi(AthenaGatewayClient(requireHttps(spec.url), spec.key.toHex()), spec.broker())

fun AthenaGatewayDownloader(spec: TransportSpec.Gateway): AthenaGatewayDownloader =
    AthenaGatewayDownloader(AthenaGatewayClient(requireHttps(spec.url), spec.key.toHex()))

/** LAN clients on Darwin, with Android's LAN timeouts, no redirects and no retries (see [lanClients]). */
fun lanHttpClients(): LanClients = lanClients(Darwin.create())
