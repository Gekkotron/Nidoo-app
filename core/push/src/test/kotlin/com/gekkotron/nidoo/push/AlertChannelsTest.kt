package com.gekkotron.nidoo.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.media.AudioAttributes
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.alerts.AlertChannel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AlertChannelsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `ensure creates the seven channels with their importance`() {
        AlertChannels(context).ensure()
        val expected = mapOf(
            "alarm" to NotificationManager.IMPORTANCE_HIGH,
            "security_chime" to NotificationManager.IMPORTANCE_HIGH,
            "camera_chime" to NotificationManager.IMPORTANCE_HIGH,
            "energy_chime" to NotificationManager.IMPORTANCE_DEFAULT,
            "climate_chime" to NotificationManager.IMPORTANCE_DEFAULT,
            "system_chime" to NotificationManager.IMPORTANCE_DEFAULT,
            "misc_chime" to NotificationManager.IMPORTANCE_DEFAULT,
        )
        assertEquals(expected.keys, manager.notificationChannels.map { it.id }.toSet())
        expected.forEach { (id, importance) -> assertEquals(importance, manager.getNotificationChannel(id).importance, id) }
    }

    @Test
    fun `names and descriptions come from resources`() {
        AlertChannels(context).ensure()
        assertEquals("Alarm", manager.getNotificationChannel("alarm").name.toString())
        assertNotNull(manager.getNotificationChannel("camera_chime").description)
        assertEquals("Other", manager.getNotificationChannel("misc_chime").name.toString())
    }

    @Test
    fun `ensure is safe to run on every launch`() {
        AlertChannels(context).ensure()
        AlertChannels(context).ensure()
        assertEquals(7, NotificationManagerCompat.from(context).notificationChannels.size)
    }

    @Test
    fun `every channel but the alarm rings the nidoo chime`() {
        AlertChannels(context).ensure()
        val chime = AlertChannels.chimeUri(context)
        assertEquals(ContentResolver.SCHEME_ANDROID_RESOURCE, chime.scheme)
        assertEquals(context.packageName, chime.authority)
        assertEquals(listOf("raw", "nidoo_chime"), chime.pathSegments)
        AlertChannel.entries.filter { it != AlertChannel.ALARM }.forEach { channel ->
            val created = manager.getNotificationChannel(AlertChannels.channelId(channel))
            assertEquals(chime, created.sound, channel.id)
            assertEquals(AudioAttributes.USAGE_NOTIFICATION, created.audioAttributes.usage, channel.id)
        }
        assertNotEquals(chime, manager.getNotificationChannel("alarm").sound)
    }

    @Test
    fun `the channels from before the chime are deleted`() {
        AlertChannel.entries.forEach { manager.createNotificationChannel(NotificationChannel(it.id, it.id, NotificationManager.IMPORTANCE_DEFAULT)) }
        AlertChannels(context).ensure()
        listOf("security", "camera", "energy", "climate", "system", "misc").forEach { assertNull(manager.getNotificationChannel(it), it) }
        assertNotNull(manager.getNotificationChannel("alarm"))
    }

    @Test
    fun `every channel has its own name, description and an opaque accent`() {
        val names = AlertChannel.entries.map { AlertChannels.nameRes(it) }
        assertEquals(names.size, names.toSet().size)
        AlertChannel.entries.forEach { assertEquals(0xFF, AlertChannels.accent(it) ushr 24, it.id) }
    }
}
