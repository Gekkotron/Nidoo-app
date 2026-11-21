package com.gekkotron.nidoo.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A bare TCP connect to the broker's port, no MQTT, on Ktor sockets (iOS; Android keeps [TcpProbe]).
 * false on timeout or any failure; the socket is always closed.
 */
class NetworkTcpProbe(
    private val host: String,
    private val port: Int,
    private val timeout: Duration = 1500.milliseconds,
    private val connect: suspend (String, Int) -> Unit = ::ktorConnect,
) : LocalProbe {
    override suspend fun reachable(): Boolean = withTimeoutOrNull(timeout) {
        try {
            connect(host, port)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false // refused, unresolved, a port outside 0..65535…
        }
    } ?: false
}

private suspend fun ktorConnect(host: String, port: Int) {
    SelectorManager(Dispatchers.IO).use { selector ->
        aSocket(selector).tcp().connect(host, port).close()
    }
}
