package com.gekkotron.nidoo.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.shared.di.iosModule
import com.gekkotron.nidoo.shared.di.sharedModule
import com.gekkotron.nidoo.transport.MqttClientPort
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

/**
 * Entry point for the iOS app (`MainViewControllerKt.MainViewController(mqttPortFactory:)` in Swift).
 * [mqttPortFactory] makes a new Swift MQTT client (CocoaMQTT) for each direct link (`PortMqttTransport`): the live
 * link and each connection test need their own connection.
 */
fun MainViewController(mqttPortFactory: () -> MqttClientPort): UIViewController = ComposeUIViewController {
    KoinApplication(configuration = koinConfiguration { modules(iosModule(mqttPortFactory), sharedModule, onboardingModule) }) {
        NidooApp()
    }
}
