package com.gekkotron.nidoo.transport

import io.github.gekkotron.athena.gateway.client.AthenaGatewayClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** [http] is shared; publishes get a 10 s call timeout (the SDK clears it for streams). */
fun AthenaGatewayApi(spec: TransportSpec.Gateway, http: OkHttpClient = OkHttpClient()): AthenaGatewayApi = AthenaGatewayApi(
    AthenaGatewayClient(
        requireHttps(spec.url),
        spec.key.toHex(),
        http.noDowngrade().newBuilder().callTimeout(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
    ),
    spec.broker(),
)

/** Refuses https to http redirects: cleartext is allowed app-wide (push alerts spec §6.3), the gateway must stay on TLS. */
internal fun OkHttpClient.noDowngrade(): OkHttpClient = newBuilder().followSslRedirects(false).build()

private const val PUBLISH_TIMEOUT_SECONDS = 10L
