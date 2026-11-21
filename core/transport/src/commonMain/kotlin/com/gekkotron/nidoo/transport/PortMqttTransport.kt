@file:OptIn(ExperimentalAtomicApi::class)

package com.gekkotron.nidoo.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * MQTT 3.1.1 to the local broker through a [MqttClientPort] (the iOS direct link). Same observable behaviour
 * as the JVM's DirectMqttTransport: Connecting → Connected(DIRECT); a lost connection is Reconnecting(attempt)
 * with [Backoff]; bad credentials and an unsupported transport go Offline without retrying; the whole
 * subscription set is re-applied after each connect. Messages are never dropped.
 */
class PortMqttTransport(
    private val spec: TransportSpec.Direct,
    private val scope: CoroutineScope,
    private val port: MqttClientPort,
    reconnectInitial: Duration = 1.seconds,
    reconnectMax: Duration = 30.seconds,
    /** Also bounds the wait for a subscribe or unsubscribe acknowledgement. */
    private val publishTimeout: Duration = 10.seconds,
) : ManagedTransport {
    private val _link = MutableStateFlow<LinkState>(LinkState.Offline(LinkState.Offline.NOT_STARTED))
    override val link: StateFlow<LinkState> = _link.asStateFlow()

    private val _failure = MutableStateFlow<DirectFailure?>(null)

    /** Why the last attempt failed; cleared on connect. Read by the onboarding connection test. */
    val failure: StateFlow<DirectFailure?> = _failure.asStateFlow()

    private val backoff = Backoff(reconnectInitial, reconnectMax)
    private val inbox = Channel<MqttMessage>(Channel.UNLIMITED)
    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 1024)
    private val mutex = Mutex()
    private var subscriptions: Set<String> = emptySet() // guarded by [mutex]
    private val attempts = AtomicInt(0)
    private val connected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val reconnect = AtomicReference<Job?>(null)
    private var pump: Job? = null

    private val listener = object : MqttPortListener {
        override fun onConnected() = this@PortMqttTransport.onConnected()
        override fun onConnectionLost(kind: PortFailure, message: String) = this@PortMqttTransport.onConnectionLost(kind, message)
        override fun onMessage(topic: String, payload: ByteArray, retained: Boolean) {
            inbox.trySend(MqttMessage(topic, payload, retained))
        }
    }

    override fun messages(): SharedFlow<MqttMessage> = _messages.asSharedFlow()

    override fun start() {
        check(pump == null) { "already started" }
        // Wait for the first collector, so nothing is emitted into a SharedFlow nobody listens to.
        pump = scope.launch {
            _messages.subscriptionCount.first { it > 0 }
            for (message in inbox) _messages.emit(message)
        }
        _link.value = LinkState.Connecting
        port.connect(spec, listener)
    }

    override suspend fun close() {
        if (closed.exchange(true)) return
        reconnect.exchange(null)?.cancel()
        connected.store(false)
        port.disconnect()
        pump?.cancel()
        inbox.close()
        _link.value = LinkState.Offline(LinkState.Offline.CLOSED)
    }

    override suspend fun setSubscriptions(filters: Set<String>) {
        val (added, removed) = mutex.withLock {
            val diff = (filters - subscriptions) to (subscriptions - filters)
            subscriptions = filters
            diff
        }
        if (!connected.load()) return // onConnected applies the whole set
        if (added.isNotEmpty()) subscribe(added.toList())
        if (removed.isNotEmpty()) unsubscribe(removed.toList())
    }

    /** As on the JVM, a publish that times out here may still reach the broker if the port delivers it later. */
    override suspend fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {
        if (!isValidPublishTopic(topic)) throw PublishFailed("invalid publish topic '$topic'")
        if (!connected.load()) throw PublishFailed("not connected")
        if (qos !in 0..2) throw PublishFailed("invalid QoS $qos")
        val error = try {
            withTimeout(publishTimeout) { await { done -> port.publish(topic, payload, qos, retain, done) } }
        } catch (e: TimeoutCancellationException) {
            throw PublishFailed("publish timed out")
        }
        if (error != null) throw PublishFailed(error)
    }

    private fun onConnected() {
        if (closed.load()) {
            port.disconnect()
            return
        }
        attempts.store(0)
        connected.store(true)
        _failure.value = null
        _link.value = LinkState.Connected(TransportMode.DIRECT)
        scope.launch {
            // Clean session: the broker forgot our subscriptions, so re-apply all of them.
            val filters = mutex.withLock { subscriptions }
            if (filters.isNotEmpty()) subscribe(filters.toList())
        }
    }

    private fun onConnectionLost(kind: PortFailure, message: String) {
        connected.store(false)
        if (closed.load()) {
            _link.value = LinkState.Offline(LinkState.Offline.CLOSED)
            return
        }
        _failure.value = when (kind) {
            PortFailure.UNREACHABLE -> DirectFailure.Unreachable
            PortFailure.REJECTED -> DirectFailure.Rejected
            PortFailure.TLS -> DirectFailure.Tls(message)
            PortFailure.UNSUPPORTED, PortFailure.OTHER -> DirectFailure.Other(message)
        }
        when (kind) {
            PortFailure.REJECTED -> _link.value = LinkState.Offline(LinkState.Offline.REJECTED)
            PortFailure.UNSUPPORTED -> _link.value = LinkState.Offline(LinkState.Offline.FAILED)
            else -> {
                val attempt = attempts.incrementAndFetch()
                _link.value = LinkState.Reconnecting(attempt)
                val job = scope.launch {
                    delay(backoff.delayFor(attempt))
                    if (!closed.load()) port.connect(spec, listener)
                }
                reconnect.exchange(job)?.cancel()
                if (closed.load()) job.cancel() // close() ran between the check above and the exchange
            }
        }
    }

    /**
     * A failed (un)subscribe means the connection dropped; onConnected re-applies the set. Malformed filters
     * are left out: a client may refuse the whole request for one of them (CocoaMQTT does, silently). An
     * answer that never comes is given up after [publishTimeout], so no caller waits forever.
     */
    private suspend fun subscribe(filters: List<String>) {
        val valid = filters.filter(::isValidSubscribeFilter)
        if (valid.isEmpty()) return
        withTimeoutOrNull(publishTimeout) { await { done -> port.subscribe(valid, QOS_AT_LEAST_ONCE, done) } }
    }

    private suspend fun unsubscribe(filters: List<String>) {
        val valid = filters.filter(::isValidSubscribeFilter)
        if (valid.isEmpty()) return
        withTimeoutOrNull(publishTimeout) { await { done -> port.unsubscribe(valid, done) } }
    }

    /** The port's error, or null; a callback that runs twice is ignored the second time. */
    private suspend fun await(call: (done: (String?) -> Unit) -> Unit): String? {
        val result = CompletableDeferred<String?>()
        call { error -> result.complete(error) }
        return result.await()
    }

    private companion object {
        const val QOS_AT_LEAST_ONCE = 1
    }
}
