package com.gekkotron.nidoo.presence

/** A position and how far off it may be: the true position lies within [accuracyM] metres of it (0 when the provider gives none). */
data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Float = 0f)

/** Fetches a current GPS fix. Injected into the presence workers so tests can run without Play Services. */
fun interface CurrentLocationSource {
    /** `null` when no fix is available (missing permission, Play Services unavailable, GPS off, timeout). */
    suspend fun current(): LocationFix?
}
