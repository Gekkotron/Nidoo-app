package com.gekkotron.nidoo

import android.content.Intent
import android.net.Uri

/** What a tapped notification or widget asks the app to show (push alerts spec §5.2, widgets spec §2.3). */
sealed interface AppLink {
    data class Alert(val id: String) : AppLink

    data class History(val entityId: String) : AppLink

    data object Settings : AppLink
}

/** `nidoo://alert/<id>` (every alert notification), `nidoo://history/<entity id>` and `nidoo://settings` (the widget). */
object DeepLinks {
    fun link(uri: Uri?): AppLink? {
        if (uri == null || uri.scheme != "nidoo") return null
        // pathSegments are already decoded.
        val first = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        return when (uri.host) {
            "alert" -> first?.let(AppLink::Alert)
            "history" -> first?.let(AppLink::History)
            "settings" -> AppLink.Settings
            else -> null
        }
    }

    /** The link a fresh tap asks for; a relaunch from Recents re-delivers the old launch intent and must not replay it. */
    fun linkToOpen(intent: Intent?): AppLink? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return link(intent.data)
    }

    fun alertId(uri: Uri?): String? = (link(uri) as? AppLink.Alert)?.id

    fun alertToOpen(intent: Intent?): String? = (linkToOpen(intent) as? AppLink.Alert)?.id
}
