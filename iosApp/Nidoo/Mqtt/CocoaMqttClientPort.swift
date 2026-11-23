import CocoaMQTT
import CocoaMQTTWebSocket
import Foundation
import Nidoo

/// The iOS direct link's MQTT 3.1.1 client: CocoaMQTT behind the Kotlin `MqttClientPort` (spec §9, MQTT gate).
/// One connection attempt per `connect`; `PortMqttTransport` owns reconnecting and resubscribing, so
/// CocoaMQTT's own auto-reconnect stays off. One instance serves one transport (Kotlin gets a factory).
/// Kotlin calls in from any thread: every entry point hops to the main queue, where CocoaMQTT also delivers
/// its delegate callbacks, so all state below is only touched on main.
final class CocoaMqttClientPort: NSObject, MqttClientPort, CocoaMQTTDelegate {
    private var client: CocoaMQTT?
    private var listener: MqttPortListener?
    /// A refused CONNACK is followed by a disconnect: only the first loss of an attempt is reported.
    private var lossReported = false
    private var pendingPublishes: [UInt16: (String?) -> Void] = [:]
    private var pendingSubscribes: [(String?) -> Void] = []
    private var pendingUnsubscribes: [(String?) -> Void] = []

    func connect(spec: TransportSpecDirect, listener: MqttPortListener) {
        onMain { self.connectOnMain(spec: spec, listener: listener) }
    }

    private func connectOnMain(spec: TransportSpecDirect, listener: MqttPortListener) {
        disconnectOnMain()
        let mqtt: CocoaMQTT
        if spec.ws {
            let socket = CocoaMQTTWebSocket(uri: "/mqtt")
            socket.enableSSL = spec.tls
            mqtt = CocoaMQTT(clientID: spec.clientId, host: spec.host, port: UInt16(spec.port), socket: socket)
        } else {
            mqtt = CocoaMQTT(clientID: spec.clientId, host: spec.host, port: UInt16(spec.port))
            mqtt.enableSSL = spec.tls // system trust: no custom certificates, untrusted CAs refused
        }
        mqtt.username = spec.username
        mqtt.password = spec.password
        mqtt.cleanSession = true
        mqtt.autoReconnect = false
        mqtt.keepAlive = 60
        mqtt.delegate = self
        client = mqtt
        self.listener = listener
        lossReported = false
        // A broker that never answers must fail the attempt (and back off) instead of hanging it.
        if !mqtt.connect(timeout: 10) {
            reportLoss(.unreachable, "could not start the connection")
        }
    }

    func subscribe(filters: [String], qos: Int32, done: @escaping (String?) -> Void) {
        onMain {
            guard let mqtt = self.client, mqtt.connState == .connected else { return done("not connected") }
            self.pendingSubscribes.append(done)
            mqtt.subscribe(filters.map { ($0, CocoaMQTTQoS(rawValue: UInt8(qos)) ?? .qos1) })
        }
    }

    func unsubscribe(filters: [String], done: @escaping (String?) -> Void) {
        onMain {
            guard let mqtt = self.client, mqtt.connState == .connected else { return done("not connected") }
            self.pendingUnsubscribes.append(done)
            mqtt.unsubscribe(filters)
        }
    }

    func publish(topic: String, payload: KotlinByteArray, qos: Int32, retain: Bool, done: @escaping (String?) -> Void) {
        let bytes = payload.toBytes()
        onMain {
            guard let mqtt = self.client, mqtt.connState == .connected else { return done("not connected") }
            let level = CocoaMQTTQoS(rawValue: UInt8(qos)) ?? .qos1
            let id = mqtt.publish(CocoaMQTTMessage(topic: topic, payload: bytes, qos: level, retained: retain))
            if id < 0 { return done("publish refused by the client") }
            // QoS 1 completes on PUBACK (didPublishAck), QoS 2 on PUBCOMP (didPublishComplete).
            if level == .qos0 { done(nil) } else { self.pendingPublishes[UInt16(id)] = done }
        }
    }

    func disconnect() {
        onMain { self.disconnectOnMain() }
    }

    private func disconnectOnMain() {
        guard let mqtt = client else { return }
        mqtt.delegate = nil
        mqtt.disconnect()
        client = nil
        failPending("disconnected")
    }

    // MARK: CocoaMQTTDelegate

    func mqtt(_ mqtt: CocoaMQTT, didConnectAck ack: CocoaMQTTConnAck) {
        guard mqtt === client else { return }
        switch ack {
        case .accept: listener?.onConnected()
        case .badUsernameOrPassword, .notAuthorized: reportLoss(.rejected, ack.description)
        default: reportLoss(.other, ack.description)
        }
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishMessage message: CocoaMQTTMessage, id: UInt16) {}

    func mqtt(_ mqtt: CocoaMQTT, didPublishAck id: UInt16) {
        pendingPublishes.removeValue(forKey: id)?(nil)
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishComplete id: UInt16) {
        pendingPublishes.removeValue(forKey: id)?(nil)
    }

    func mqtt(_ mqtt: CocoaMQTT, didReceiveMessage message: CocoaMQTTMessage, id: UInt16) {
        guard mqtt === client else { return }
        listener?.onMessage(topic: message.topic, payload: KotlinByteArray.from(message.payload), retained: message.retained)
    }

    func mqtt(_ mqtt: CocoaMQTT, didSubscribeTopics success: NSDictionary, failed: [String]) {
        guard !pendingSubscribes.isEmpty else { return }
        pendingSubscribes.removeFirst()(failed.isEmpty ? nil : "refused: \(failed.joined(separator: ", "))")
    }

    func mqtt(_ mqtt: CocoaMQTT, didUnsubscribeTopics topics: [String]) {
        guard !pendingUnsubscribes.isEmpty else { return }
        pendingUnsubscribes.removeFirst()(nil)
    }

    func mqttDidPing(_ mqtt: CocoaMQTT) {}

    func mqttDidReceivePong(_ mqtt: CocoaMQTT) {}

    func mqttDidDisconnect(_ mqtt: CocoaMQTT, withError err: Error?) {
        guard mqtt === client else { return }
        failPending("connection lost")
        reportLoss(Self.classify(err), err?.localizedDescription ?? "the broker closed the connection")
    }

    // MARK: Helpers

    /// Runs [work] on the main queue: at once when already there, otherwise in order after earlier calls.
    private func onMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread { work() } else { DispatchQueue.main.async(execute: work) }
    }

    private func reportLoss(_ kind: PortFailure, _ message: String) {
        guard !lossReported else { return }
        lossReported = true
        listener?.onConnectionLost(kind: kind, message: message)
    }

    private func failPending(_ reason: String) {
        let callbacks = Array(pendingPublishes.values) + pendingSubscribes + pendingUnsubscribes
        pendingPublishes.removeAll()
        pendingSubscribes.removeAll()
        pendingUnsubscribes.removeAll()
        callbacks.forEach { $0(reason) }
    }

    /// TLS failures carry Secure Transport codes (-9899…-9800) or a TLS/SSL domain; refused, unreachable
    /// and timed-out sockets are network errors; anything else is retried as `other`.
    private static func classify(_ err: Error?) -> PortFailure {
        guard let error = err as NSError? else { return .other }
        if (-9899 ... -9800).contains(error.code) || error.domain.contains("SSL") || error.domain.contains("TLS") {
            return .tls
        }
        if error.code == NSURLErrorServerCertificateUntrusted || error.code == NSURLErrorSecureConnectionFailed
            || error.code == NSURLErrorServerCertificateHasUnknownRoot || error.code == NSURLErrorServerCertificateHasBadDate {
            return .tls
        }
        if error.domain == NSPOSIXErrorDomain || error.domain == NSURLErrorDomain || error.domain.contains("AsyncSocket") {
            return .unreachable
        }
        return .other
    }
}

private extension KotlinByteArray {
    func toBytes() -> [UInt8] {
        (0 ..< size).map { UInt8(bitPattern: get(index: $0)) }
    }

    static func from(_ bytes: [UInt8]) -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(bytes.count))
        for (i, b) in bytes.enumerated() { array.set(index: Int32(i), value: Int8(bitPattern: b)) }
        return array
    }
}
