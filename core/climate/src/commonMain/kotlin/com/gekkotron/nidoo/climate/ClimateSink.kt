package com.gekkotron.nidoo.climate

/** Where the MQTT layer feeds retained Climat zone documents. May be called from any thread. */
interface ClimateSink {
    /** [payload] null or empty means the retained document was deleted. */
    fun onRetained(zone: String, doc: ClimateDoc, payload: String?)

    /** Drops every zone (a new MQTT source is attaching). */
    fun clear()
}
