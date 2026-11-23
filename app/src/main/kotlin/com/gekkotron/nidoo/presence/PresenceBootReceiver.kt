package com.gekkotron.nidoo.presence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gekkotron.nidoo.NidooApplication

/** Puts the home geofence back after a reboot, which clears every geofence Play services held for the app. */
class PresenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? NidooApplication ?: return
        val pending = goAsync()
        app.presenceGeofenceManager.reapply { pending.finish() }
    }
}
