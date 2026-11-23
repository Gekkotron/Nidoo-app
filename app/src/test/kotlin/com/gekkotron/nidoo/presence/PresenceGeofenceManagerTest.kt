package com.gekkotron.nidoo.presence

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.PresenceConfig
import com.gekkotron.nidoo.data.create
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PresenceGeofenceManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val calls = mutableListOf<String>()

    /** A client whose every call fails the way Play services does while location is switched off. */
    private val unavailable = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(GeofencingClient::class.java)) { _, method, _ ->
        calls += method.name
        Tasks.forException<Void>(ApiException(Status(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)))
    } as GeofencingClient

    @Test
    fun `a registration Play services refuses is survived, not thrown into the application scope`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val prefs = AppPreferences.create(tmp.newFile().also { it.delete() }, scope)
        val manager = PresenceGeofenceManager(context, prefs, scope, unavailable)
        runBlocking { manager.apply(PresenceConfig(enabled = true, alias = "pixel", homeLat = 48.0, homeLng = 2.0)) }
        assertEquals(listOf("removeGeofences", "addGeofences"), calls)
    }
}
