package com.gekkotron.nidoo.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.gekkotron.nidoo.shared.di.sharedModule
import com.gekkotron.nidoo.transport.MqttClientPort
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

/**
 * Entry point for the iOS app (`MainViewControllerKt.MainViewController(mqttPortFactory:)` in Swift).
 * [mqttPortFactory] makes a new Swift MQTT client (CocoaMQTT) for each direct link (`PortMqttTransport`): the live
 * link, a connection test and a one-shot publish each need their own connection. The Phase 4 shell wires it in.
 */
fun MainViewController(mqttPortFactory: () -> MqttClientPort): UIViewController {
    IosPlatform.mqttPortFactory = mqttPortFactory
    return ComposeUIViewController {
        KoinApplication(configuration = koinConfiguration { modules(sharedModule) }) { NidooRoot() }
    }
}

/** What the Swift side hands to Kotlin at startup. */
internal object IosPlatform {
    var mqttPortFactory: (() -> MqttClientPort)? = null
}
