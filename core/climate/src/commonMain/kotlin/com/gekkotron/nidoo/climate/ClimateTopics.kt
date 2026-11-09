package com.gekkotron.nidoo.climate

/** The hub's Climat topics under [PREFIX]: retained zone documents and the hold command. */
object ClimateTopics {
    const val PREFIX = "nidoo/climate"

    /** The zone and document of a retained zone document topic; null for anything else (house, proposal, hold command…). */
    fun parse(topic: String): Pair<String, ClimateDoc>? {
        val parts = topic.removePrefix("$PREFIX/").takeIf { it != topic }?.split('/') ?: return null
        if (parts.size != 3 || parts[0] != "zone" || parts[1].isEmpty()) return null
        val doc = when (parts[2]) {
            "settings" -> ClimateDoc.SETTINGS
            "decision" -> ClimateDoc.DECISION
            "override" -> ClimateDoc.OVERRIDE
            else -> return null
        }
        return parts[1] to doc
    }

    /** Where the app sends a boost (or an empty payload to end it). Never retained. */
    fun holdCommand(zone: String): String = "$PREFIX/zone/$zone/hold/set"
}

/** Payloads for [ClimateTopics.holdCommand]. */
object ClimateCommands {
    /** A boost to [setpoint] °C for [minutes]. */
    fun boost(setpoint: Double, minutes: Int): String = """{"setpoint":$setpoint,"minutes":$minutes}"""

    /** Ends the zone's boost; the hub keeps holds made on the devices. */
    const val CANCEL = ""
}
