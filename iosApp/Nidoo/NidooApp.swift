import SwiftUI

@main
struct NidooApp: App {
    /// The direct MQTT link's client, handed to Kotlin once for the app's lifetime.
    private let mqttPort = CocoaMqttClientPort()

    var body: some Scene {
        WindowGroup {
            ComposeView(mqttPort: mqttPort).ignoresSafeArea()
        }
    }
}
