package com.gekkotron.nidoo.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTcpProbeTest {
    @Test
    fun reachableWhenListening() = runTest {
        // Real time: the socket work runs on IO threads that virtual time does not wait for.
        withContext(Dispatchers.Default) {
            SelectorManager(Dispatchers.IO).use { selector ->
                aSocket(selector).tcp().bind("127.0.0.1", 0).use { server ->
                    val port = (server.localAddress as InetSocketAddress).port
                    assertTrue(NetworkTcpProbe("127.0.0.1", port).reachable())
                }
            }
        }
    }

    @Test
    fun unreachableWhenClosed() = runTest {
        withContext(Dispatchers.Default) {
            val port = SelectorManager(Dispatchers.IO).use { selector ->
                aSocket(selector).tcp().bind("127.0.0.1", 0).use { (it.localAddress as InetSocketAddress).port }
            }
            assertFalse(NetworkTcpProbe("127.0.0.1", port).reachable())
            assertFalse(NetworkTcpProbe("127.0.0.1", 70_000).reachable())
        }
    }

    @Test
    fun timesOutAsFalse() = runTest {
        val probe = NetworkTcpProbe("10.255.255.1", 1883, connect = { _, _ -> awaitCancellation() })
        val result = async { probe.reachable() }
        advanceTimeBy(1499.milliseconds)
        assertFalse(result.isCompleted)
        advanceTimeBy(2.milliseconds)
        assertFalse(result.await())
    }
}
