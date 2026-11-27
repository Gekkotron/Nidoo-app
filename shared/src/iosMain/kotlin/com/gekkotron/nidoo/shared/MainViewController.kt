package com.gekkotron.nidoo.shared

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.gekkotron.nidoo.feature.automations.di.automationsModule
import com.gekkotron.nidoo.feature.energy.di.energyModule
import com.gekkotron.nidoo.feature.history.di.historyModule
import com.gekkotron.nidoo.feature.home.di.homeModule
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.feature.security.di.securityModule
import com.gekkotron.nidoo.feature.settings.di.settingsModule
import com.gekkotron.nidoo.shared.di.iosModule
import com.gekkotron.nidoo.shared.di.mainTabsModule
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
fun MainViewController(mqttPortFactory: () -> MqttClientPort): UIViewController {
    // Home's media covers are http(s) images: Coil needs its Ktor fetcher (Darwin engine) registered on iOS.
    SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build() }
    return ComposeUIViewController {
        KoinApplication(configuration = koinConfiguration { modules(iosModule(mqttPortFactory), sharedModule, onboardingModule, homeModule, settingsModule, energyModule, historyModule, securityModule, automationsModule, mainTabsModule) }) {
            NidooApp()
        }
    }
}
