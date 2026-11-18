package com.gekkotron.nidoo.presence

import com.gekkotron.nidoo.data.PresenceConfig
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chooses [PresenceState] from a GPS fix and a [PresenceConfig]. Pure logic (haversine), tested as JVM.
 *
 * AWAY only when the fix is outside the radius by more than its own accuracy: an imprecise indoor fix that lands just
 * past the edge could still be at home, and a wrong AWAY arms whatever the hub ties to presence.
 */
object PresenceDecision {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** `null` when there is no home, or the fix is too imprecise to tell which side of the radius the phone is on. */
    fun stateOf(fix: LocationFix, config: PresenceConfig): PresenceState? {
        val homeLat = config.homeLat ?: return null
        val homeLng = config.homeLng ?: return null
        val distance = haversineMeters(fix.lat, fix.lng, homeLat, homeLng)
        return when {
            distance <= config.radiusM -> PresenceState.HOME
            distance - fix.accuracyM > config.radiusM -> PresenceState.AWAY
            else -> null
        }
    }

    private fun haversineMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val lat1 = Math.toRadians(aLat)
        val lat2 = Math.toRadians(bLat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }
}
