package com.gekkotron.nidoo.climate

/** Where the MQTT layer feeds retained Climat zone documents. May be called from any thread. */
interface ClimateSink {
    /** [payload] null or empty means the retained document was deleted. */
    fun onRetained(zone: String, doc: ClimateDoc, payload: String?)

    /** The MQTT link came up: documents the broker does not replay soon are gone (deleted while offline). */
    fun markLinkUp()

    /** Drops every zone (a new MQTT source is attaching). */
    fun clear()
}
