package com.gekkotron.nidoo.transport

/**
 * The low-level MQTT 3.1.1 client the iOS direct link drives (CocoaMQTT on iOS, spec §9). Callback-based so
 * Swift can implement it. Callbacks may arrive on any thread. Clean session; [PortMqttTransport] re-applies
 * its subscriptions after every connect and owns reconnecting, so the port must not reconnect on its own.
 */
interface MqttClientPort {
    /** Starts one connection attempt; the outcome arrives through [listener]. Websockets use the path `/mqtt`. */
    fun connect(spec: TransportSpec.Direct, listener: MqttPortListener)

    fun subscribe(filters: List<String>, qos: Int, done: (error: String?) -> Unit)

    fun unsubscribe(filters: List<String>, done: (error: String?) -> Unit)

    /** [done] runs once the broker has acknowledged a QoS 1 publish (at once for QoS 0), or with an error. */
    fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean, done: (error: String?) -> Unit)

    fun disconnect()
}

interface MqttPortListener {
    fun onConnected()

    /** A failed attempt or a dropped connection. */
    fun onConnectionLost(kind: PortFailure, message: String)

    fun onMessage(topic: String, payload: ByteArray, retained: Boolean)
}

/** Why a connection failed: [REJECTED] (bad credentials) and [UNSUPPORTED] (e.g. a transport the client lacks) are not retried. */
enum class PortFailure { UNREACHABLE, REJECTED, TLS, UNSUPPORTED, OTHER }
