package com.gekkotron.oikos.alerts

/** The push topic contract (`push-in-hub` spec §3). */
object PushTopics {
    const val DEFAULT_TOKEN_TOPIC = "nidoo/push/tokens/{installId}"

    /** Oikos to producers; not retained. */
    const val RESPONSES = "nidoo/push/responses"

    /** The hub to Oikos; retained JSON array of normalized alerts. */
    const val HISTORY = "nidoo/push/history"

    /** The profile's `pushTokenTopic` (or the default) with `{installId}` substituted. */
    fun tokenTopic(template: String?, installId: String): String =
        (template?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN_TOPIC).replace("{installId}", installId)
}
