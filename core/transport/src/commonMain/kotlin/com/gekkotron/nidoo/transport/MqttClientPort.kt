package com.gekkotron.nidoo.transport

/**
 * The low-level MQTT 3.1.1 client the iOS direct link drives (CocoaMQTT on iOS, spec §9). Callback-based so
 * Swift can implement it. Clean session; [PortMqttTransport] re-applies its subscriptions after every connect
 * and owns reconnecting, so the port must not reconnect on its own.
 *
 * - One instance serves one [PortMqttTransport]: each transport gets its own from a factory, so a connection
 *   test never takes over the live link's connection.
 * - Methods may be called from any thread; the implementation serialises them itself (the Swift one runs them
 *   on the main queue). Listener and `done` callbacks may arrive on any thread.
 * - `done` runs exactly once per call, including for QoS 2 publishes (on PUBCOMP).
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
