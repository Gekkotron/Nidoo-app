package com.gekkotron.nidoo.transport

import io.github.gekkotron.athena.gateway.client.AthenaGatewayClient
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Duration.Companion.seconds

/**
 * The gateway link on iOS: the SDK's own Darwin client, which never follows redirects (so https can't be
 * downgraded). The SDK's iOS factory takes no call timeout, so publishes get the JVM's 10 s here.
 */
fun AthenaGatewayApi(spec: TransportSpec.Gateway): AthenaGatewayApi =
    AthenaGatewayApi(AthenaGatewayClient(requireHttps(spec.url), spec.key.toHex()), spec.broker(), publishTimeout = 10.seconds)

fun AthenaGatewayDownloader(spec: TransportSpec.Gateway): AthenaGatewayDownloader =
    AthenaGatewayDownloader(AthenaGatewayClient(requireHttps(spec.url), spec.key.toHex()))

/** LAN clients on Darwin, with Android's LAN timeouts, no redirects and no retries (see [lanClients]). */
fun lanHttpClients(): LanClients = lanClients(Darwin.create())
