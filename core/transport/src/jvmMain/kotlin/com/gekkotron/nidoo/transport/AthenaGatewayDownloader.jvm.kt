package com.gekkotron.nidoo.transport

import io.github.gekkotron.athena.gateway.client.AthenaGatewayClient
import okhttp3.OkHttpClient

fun AthenaGatewayDownloader(spec: TransportSpec.Gateway, http: OkHttpClient = OkHttpClient()): AthenaGatewayDownloader =
    AthenaGatewayDownloader(AthenaGatewayClient(requireHttps(spec.url), spec.key.toHex(), http.noDowngrade()))
