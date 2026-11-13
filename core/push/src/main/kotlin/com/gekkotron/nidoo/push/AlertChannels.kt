package com.gekkotron.nidoo.push

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.gekkotron.nidoo.alerts.AlertChannel

object NotificationIds {
    /** Every alert notification; the alert id is the tag, so the same id replaces (spec §5.2). */
    const val ALERT = 1
    const val REPLY_FAILED = 2
    const val REPLY_PROGRESS = 3
}

/** The seven Android notification channels (push alerts spec §5.1). Run [ensure] in `Application.onCreate`. */
class AlertChannels(private val context: Context) {
    fun ensure() {
        val manager = NotificationManagerCompat.from(context)
        val chime = chimeUri(context)
        AlertChannel.entries.forEach { channel ->
            val builder = NotificationChannelCompat.Builder(channelId(channel), importance(channel))
                .setName(context.getString(nameRes(channel)))
                .setDescription(context.getString(descriptionRes(channel)))
            if (chimes(channel)) builder.setSound(chime, CHIME_ATTRIBUTES)
            manager.createNotificationChannel(builder.build())
            // The channel it replaces still rings the phone's default sound; Android never changes it in place.
            if (channelId(channel) != channel.id) manager.deleteNotificationChannel(channel.id)
        }
    }

    companion object {
        private const val CHIME_SUFFIX = "_chime"

        private val CHIME_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Every channel rings the Nidoo chime (the web logo's songbird call) but the alarm, which keeps the phone's louder sound. */
        fun chimes(channel: AlertChannel): Boolean = channel != AlertChannel.ALARM

        /**
         * The Android channel id. A channel's sound is fixed once it exists, so the chiming channels live under new ids
         * and [ensure] deletes the old ones; [AlertChannel.id] stays the wire and database id.
         */
        fun channelId(channel: AlertChannel): String = if (chimes(channel)) channel.id + CHIME_SUFFIX else channel.id

        /** By name, not by resource id: a channel keeps this URI across updates, and ids can move between builds. */
        fun chimeUri(context: Context): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(context.resources.getResourceTypeName(R.raw.nidoo_chime))
            .appendPath(context.resources.getResourceEntryName(R.raw.nidoo_chime))
            .build()

        /** HIGH for the alarm, security and camera channels; DEFAULT for the rest. */
        fun importance(channel: AlertChannel): Int = when (channel) {
            AlertChannel.ALARM, AlertChannel.SECURITY, AlertChannel.CAMERA -> NotificationManagerCompat.IMPORTANCE_HIGH
            AlertChannel.ENERGY, AlertChannel.CLIMATE, AlertChannel.SYSTEM, AlertChannel.MISC -> NotificationManagerCompat.IMPORTANCE_DEFAULT
        }

        /** The one small icon is monochrome; the channel only changes its accent colour. */
        @ColorInt
        fun accent(channel: AlertChannel): Int = when (channel) {
            AlertChannel.ALARM -> 0xFFE5484D.toInt()
            AlertChannel.SECURITY -> 0xFFF76B15.toInt()
            AlertChannel.CAMERA -> 0xFF3E63DD.toInt()
            AlertChannel.ENERGY -> 0xFFFFB224.toInt()
            AlertChannel.CLIMATE -> 0xFF12A594.toInt()
            AlertChannel.SYSTEM -> 0xFF8B8D98.toInt()
            AlertChannel.MISC -> 0xFF6E6F78.toInt()
        }

        @StringRes
        fun nameRes(channel: AlertChannel): Int = when (channel) {
            AlertChannel.ALARM -> R.string.push_channel_alarm
            AlertChannel.SECURITY -> R.string.push_channel_security
            AlertChannel.CAMERA -> R.string.push_channel_camera
            AlertChannel.ENERGY -> R.string.push_channel_energy
            AlertChannel.CLIMATE -> R.string.push_channel_climate
            AlertChannel.SYSTEM -> R.string.push_channel_system
            AlertChannel.MISC -> R.string.push_channel_misc
        }

        @StringRes
        fun descriptionRes(channel: AlertChannel): Int = when (channel) {
            AlertChannel.ALARM -> R.string.push_channel_alarm_desc
            AlertChannel.SECURITY -> R.string.push_channel_security_desc
            AlertChannel.CAMERA -> R.string.push_channel_camera_desc
            AlertChannel.ENERGY -> R.string.push_channel_energy_desc
            AlertChannel.CLIMATE -> R.string.push_channel_climate_desc
            AlertChannel.SYSTEM -> R.string.push_channel_system_desc
            AlertChannel.MISC -> R.string.push_channel_misc_desc
        }
    }
}
