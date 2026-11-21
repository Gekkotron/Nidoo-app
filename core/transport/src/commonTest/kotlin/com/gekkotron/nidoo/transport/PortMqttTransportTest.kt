package com.gekkotron.nidoo.transport

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The scenarios of DirectMqttTransportTest (which runs HiveMQ against a broker), driven through a fake port. */
@OptIn(ExperimentalCoroutinesApi::class)
class PortMqttTransportTest {
    private class FakeMqttClientPort : MqttClientPort {
        var listener: MqttPortListener? = null
        val connects = mutableListOf<TransportSpec.Direct>()
        val subscribes = mutableListOf<List<String>>()
        val unsubscribes = mutableListOf<List<String>>()
        val publishes = mutableListOf<Triple<String, String, Pair<Int, Boolean>>>()
        val publishDone = mutableListOf<(String?) -> Unit>()
        var disconnects = 0
        var autoCompletePublish = true
        var autoCompleteSubscribe = true

        override fun connect(spec: TransportSpec.Direct, listener: MqttPortListener) {
            connects += spec
            this.listener = listener
        }

        override fun subscribe(filters: List<String>, qos: Int, done: (error: String?) -> Unit) {
            assertEquals(1, qos)
            subscribes += filters
            if (autoCompleteSubscribe) done(null)
        }

        override fun unsubscribe(filters: List<String>, done: (error: String?) -> Unit) {
            unsubscribes += filters
            done(null)
        }

        override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean, done: (error: String?) -> Unit) {
            publishes += Triple(topic, payload.decodeToString(), qos to retain)
            if (autoCompletePublish) done(null) else publishDone += done
        }

        override fun disconnect() {
            disconnects++
        }

        fun connected() = checkNotNull(listener).onConnected()
        fun lost(kind: PortFailure, message: String = kind.name) = checkNotNull(listener).onConnectionLost(kind, message)
        fun deliver(topic: String, payload: String, retained: Boolean = false) =
            checkNotNull(listener).onMessage(topic, payload.encodeToByteArray(), retained)
    }

    private val spec = TransportSpec.Direct("192.168.1.91", 1883, tls = false, ws = false, username = "nidoo", password = "p@ss wörd:1", clientId = "nidoo-test")
    private val port = FakeMqttClientPort()

    private fun TestScope.transport(s: TransportSpec.Direct = spec) = PortMqttTransport(
        s, backgroundScope, port, reconnectInitial = 200.milliseconds, reconnectMax = 1.seconds, publishTimeout = 2.seconds,
    )

    private fun TestScope.record(t: MqttTransport): MutableList<MqttMessage> {
        val list = mutableListOf<MqttMessage>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { t.messages().collect { list += it } }
        return list
    }

    private fun TestScope.connected(): PortMqttTransport = transport().also {
        it.start()
        port.connected()
        runCurrent()
    }

    @Test
    fun connectsAndReportsConnectedDirect() = runTest {
        val t = transport()
        assertEquals(LinkState.Offline(LinkState.Offline.NOT_STARTED), t.link.value)
        t.start()
        assertEquals(LinkState.Connecting, t.link.value)
        port.connected()
        assertEquals(LinkState.Connected(TransportMode.DIRECT), t.link.value)
        assertEquals(null, t.failure.value)
    }

    @Test
    fun theSpecWithItsCredentialsReachesThePort() = runTest {
        transport().start()
        assertEquals(listOf(spec), port.connects)
    }

    @Test
    fun badCredentialsGoOfflineWithoutRetrying() = runTest {
        val t = transport()
        t.start()
        port.lost(PortFailure.REJECTED)
        assertEquals(LinkState.Offline(LinkState.Offline.REJECTED), t.link.value)
        assertEquals(DirectFailure.Rejected, t.failure.value)
        advanceTimeBy(5.seconds)
        assertEquals(1, port.connects.size)
        assertEquals(LinkState.Offline(LinkState.Offline.REJECTED), t.link.value)
    }

    @Test
    fun unsupportedWsIsOffline() = runTest {
        val t = transport(spec.copy(ws = true))
        t.start()
        port.lost(PortFailure.UNSUPPORTED, "websocket not supported")
        assertEquals(LinkState.Offline(LinkState.Offline.FAILED), t.link.value)
        assertEquals(DirectFailure.Other("websocket not supported"), t.failure.value)
        advanceTimeBy(5.seconds)
        assertEquals(1, port.connects.size)
    }

    @Test
    fun unreachableBrokerReportsUnreachableAndKeepsRetrying() = runTest {
        val t = transport()
        t.start()
        port.lost(PortFailure.UNREACHABLE)
        assertEquals(DirectFailure.Unreachable, t.failure.value)
        assertEquals(LinkState.Reconnecting(1), t.link.value)
        advanceTimeBy(241.milliseconds) // 200 ms + 20 % jitter at most
        assertEquals(2, port.connects.size)
        port.lost(PortFailure.UNREACHABLE)
        assertEquals(LinkState.Reconnecting(2), t.link.value)
        advanceTimeBy(481.milliseconds)
        assertEquals(3, port.connects.size)
        port.connected()
        assertEquals(LinkState.Connected(TransportMode.DIRECT), t.link.value)
        assertEquals(null, t.failure.value)
        port.lost(PortFailure.OTHER, "reset")
        assertEquals(LinkState.Reconnecting(1), t.link.value) // attempts restart after a success
        assertEquals(DirectFailure.Other("reset"), t.failure.value)
    }

    @Test
    fun tlsFailureIsTls() = runTest {
        val t = transport(spec.copy(tls = true, port = 8883))
        t.start()
        port.lost(PortFailure.TLS, "certificate not trusted")
        assertEquals(DirectFailure.Tls("certificate not trusted"), t.failure.value)
        assertEquals(LinkState.Reconnecting(1), t.link.value)
    }

    @Test
    fun receivesRetainedMessagesOnSubscribe() = runTest {
        val t = transport()
        val got = record(t)
        t.start()
        port.connected()
        t.setSubscriptions(setOf("homeassistant/#"))
        assertEquals(listOf(listOf("homeassistant/#")), port.subscribes)
        port.deliver("homeassistant/light/a/config", "{}", retained = true)
        runCurrent()
        assertEquals(1, got.size)
        assertEquals("homeassistant/light/a/config", got.single().topic)
        assertTrue(got.single().retained)
    }

    @Test
    fun subscriptionDiffUnsubscribesRemovedFilters() = runTest {
        val t = connected()
        t.setSubscriptions(setOf("a/#", "b/#"))
        t.setSubscriptions(setOf("b/#"))
        assertEquals(listOf(listOf("a/#", "b/#")), port.subscribes)
        assertEquals(listOf(listOf("a/#")), port.unsubscribes)
    }

    @Test
    fun subscriptionsSetWhileOfflineAreAppliedOnConnect() = runTest {
        val t = transport()
        t.setSubscriptions(setOf("x/#"))
        t.start()
        assertEquals(emptyList(), port.subscribes)
        port.connected()
        runCurrent()
        assertEquals(listOf(listOf("x/#")), port.subscribes)
    }

    /** CocoaMQTT drops a whole SUBSCRIBE, silently, when one filter is malformed: only valid filters reach the port. */
    @Test
    fun invalidFiltersAreDroppedAndTheRestSubscribed() = runTest {
        val t = connected()
        t.setSubscriptions(linkedSetOf("ok/#", "a/#/b", "", "x+/y", "nul\u0000/z", "fine/+/state"))
        assertEquals(listOf(listOf("ok/#", "fine/+/state")), port.subscribes)
        port.lost(PortFailure.UNREACHABLE)
        advanceTimeBy(241.milliseconds)
        port.connected()
        runCurrent()
        assertEquals(listOf("ok/#", "fine/+/state"), port.subscribes.last())
    }

    /** A subscribe the port never answers must not hold the caller (the discovery source's mutex) forever. */
    @Test
    fun aSubscribeWithNoAnswerTimesOut() = runTest {
        val t = connected()
        port.autoCompleteSubscribe = false
        val call = async { t.setSubscriptions(setOf("a/#")) }
        advanceTimeBy(2.seconds + 1.milliseconds)
        assertTrue(call.isCompleted)
    }

    @Test
    fun resubscribesAfterTheBrokerRestarts() = runTest {
        val t = connected()
        t.setSubscriptions(setOf("x/#", "y/#"))
        port.lost(PortFailure.UNREACHABLE)
        advanceTimeBy(241.milliseconds)
        port.connected()
        runCurrent()
        assertEquals(listOf(listOf("x/#", "y/#"), listOf("x/#", "y/#")), port.subscribes)
    }

    @Test
    fun qos1PublishReachesThePort() = runTest {
        val t = connected()
        t.publish("cmd/lamp", "ON".encodeToByteArray(), qos = 1, retain = true)
        assertEquals(listOf(Triple("cmd/lamp", "ON", 1 to true)), port.publishes)
    }

    @Test
    fun publishWhileDisconnectedThrowsPublishFailed() = runTest {
        val t = transport() // never started
        assertFailsWith<PublishFailed> { t.publish("a/b", byteArrayOf(1)) }
        t.start()
        port.connected()
        port.lost(PortFailure.UNREACHABLE)
        assertFailsWith<PublishFailed> { t.publish("a/b", byteArrayOf(1)) }
        assertEquals(emptyList(), port.publishes)
    }

    @Test
    fun invalidTopicOrQosIsPublishFailed() = runTest {
        val t = connected()
        assertFailsWith<PublishFailed> { t.publish("a/#", byteArrayOf(1)) }
        assertFailsWith<PublishFailed> { t.publish("a/b", byteArrayOf(1), qos = 3) }
        assertEquals(emptyList(), port.publishes)
    }

    @Test
    fun aPublishErrorIsPublishFailed() = runTest {
        val t = connected()
        port.autoCompletePublish = false
        val result = async { runCatching { t.publish("a/b", byteArrayOf(1)) } }
        runCurrent()
        port.publishDone.single()("broker went away")
        assertEquals("broker went away", (result.await().exceptionOrNull() as PublishFailed).message)
    }

    @Test
    fun aPublishWithNoAnswerTimesOut() = runTest {
        val t = connected()
        port.autoCompletePublish = false
        val result = async { runCatching { t.publish("a/b", byteArrayOf(1)) } }
        advanceTimeBy(2.seconds + 1.milliseconds)
        assertTrue(result.await().exceptionOrNull() is PublishFailed)
        port.publishDone.single()(null) // a late answer is ignored
    }

    @Test
    fun aBurstOf1000MessagesArrivesCompleteAndInOrder() = runTest {
        val t = transport()
        val got = record(t)
        t.start()
        port.connected()
        t.setSubscriptions(setOf("burst/#"))
        repeat(1000) { port.deliver("burst/n", "$it") }
        runCurrent()
        assertEquals((0 until 1000).map { "$it" }, got.map { it.payloadString() })
    }

    @Test
    fun closeGoesOfflineAndStopsReconnecting() = runTest {
        val t = connected()
        port.lost(PortFailure.UNREACHABLE) // a reconnect is now pending
        t.close()
        assertEquals(LinkState.Offline(LinkState.Offline.CLOSED), t.link.value)
        assertEquals(1, port.disconnects)
        advanceTimeBy(5.seconds)
        assertEquals(1, port.connects.size)
        port.connected() // a connect that was already under way
        assertEquals(LinkState.Offline(LinkState.Offline.CLOSED), t.link.value)
        assertEquals(2, port.disconnects)
        t.close() // idempotent
        assertEquals(LinkState.Offline(LinkState.Offline.CLOSED), t.link.value)
    }
}
