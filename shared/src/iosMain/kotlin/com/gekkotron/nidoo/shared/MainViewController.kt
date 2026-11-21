package com.gekkotron.nidoo.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.gekkotron.nidoo.transport.MqttClientPort
import platform.UIKit.UIViewController

/**
 * Entry point for the iOS app (`MainViewControllerKt.MainViewController(mqttPort:)` in Swift). [mqttPort] is the
 * Swift MQTT client (CocoaMQTT) that the direct link (`PortMqttTransport`) drives; the Phase 4 shell wires it in.
 */
fun MainViewController(mqttPort: MqttClientPort): UIViewController {
    IosPlatform.mqttPort = mqttPort
    return ComposeUIViewController { NidooRoot() }
}

/** What the Swift side hands to Kotlin at startup. */
internal object IosPlatform {
    var mqttPort: MqttClientPort? = null
}
