package com.gekkotron.oikos.dashboards

/** Where the MQTT layer feeds retained dashboard payloads. May be called from any thread. */
interface DashboardsSink {
    /** [payload] null or empty means the retained message was deleted. */
    fun onRetained(id: String, payload: String?)

    /** The source's own retained replay has gone quiet: the dashboards received so far are complete. */
    fun markReplayed()

    /** The MQTT link came up; starts the fallback settle timer. */
    fun markLinkUp()

    /** Drops all dashboards and the settled state (a new MQTT source is attaching). */
    fun clear()
}
