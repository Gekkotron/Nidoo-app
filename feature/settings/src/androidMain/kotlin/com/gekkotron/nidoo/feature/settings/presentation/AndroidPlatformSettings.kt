package com.gekkotron.nidoo.feature.settings.presentation

import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.push.PushStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Android's extra House rows: Notifications, Presence and Voice, in that order. */
class AndroidPlatformSettings(
    private val push: PushStatusSource,
    private val preferences: AppPreferences,
    private val availability: SpeechAvailability.Status,
) : PlatformSettings {
    override val rows: Flow<List<PlatformRow>> =
        combine(push.status, preferences.presence, preferences.voiceEnabled) { status, presence, voice ->
            rowsOf(status, presence?.enabled == true, voice == true)
        }

    override fun currentRows(): List<PlatformRow> =
        rowsOf(push.status.value, preferences.presence.value?.enabled == true, preferences.voiceEnabled.value == true)

    override fun refresh() = push.refresh()

    private fun rowsOf(status: PushStatus, presenceOn: Boolean, voiceOn: Boolean) = listOf(
        PlatformRow(SettingsPage.NOTIFICATIONS, pushStatus(status)),
        PlatformRow(SettingsPage.PRESENCE, onOff(presenceOn)),
        // Same rule as the page's toggle: on only when the phone can actually listen.
        PlatformRow(SettingsPage.VOICE, onOff(voiceOn && availability == SpeechAvailability.Status.Ready)),
    )

    private fun onOff(on: Boolean) = if (on) RowStatus.ON else RowStatus.OFF

    private fun pushStatus(status: PushStatus) = when (status) {
        is PushStatus.Registered -> RowStatus.PUSH_REGISTERED
        PushStatus.Waiting -> RowStatus.PUSH_WAITING
        is PushStatus.Failed -> RowStatus.PUSH_FAILED
        PushStatus.NotificationsOff -> RowStatus.PUSH_OFF
        PushStatus.Demo -> RowStatus.NONE
    }
}
