import SwiftUI
import UIKit
import Nidoo

/// Hosts the shared Compose Multiplatform root from the Kotlin `Nidoo` framework.
struct ComposeView: UIViewControllerRepresentable {
    let mqttPort: MqttClientPort

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(mqttPort: mqttPort)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
