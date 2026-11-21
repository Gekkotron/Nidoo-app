import SwiftUI
import UIKit
import Nidoo

/// Hosts the shared Compose Multiplatform root from the Kotlin `Nidoo` framework.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // One CocoaMQTT client per direct link: Kotlin calls this for each PortMqttTransport.
        MainViewControllerKt.MainViewController(mqttPortFactory: { CocoaMqttClientPort() })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
