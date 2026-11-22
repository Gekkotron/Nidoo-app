package com.gekkotron.nidoo.transport

import kotlinx.coroutines.flow.StateFlow

/**
 * A direct MQTT link to the local broker (HiveMQ on Android, CocoaMQTT through [PortMqttTransport] on iOS), with why
 * its last attempt failed: the onboarding connection test reads [failure] to tell a refused password from a missing broker.
 */
interface DirectLink : ManagedTransport {
    /** Why the last attempt failed; null after a connect. */
    val failure: StateFlow<DirectFailure?>
}
