package com.gekkotron.nidoo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.push.PushStatusSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The state and actions of the Android-only Settings pages: Notifications (push) and Voice. */
class AndroidSettingsViewModel(
    private val pushSource: PushStatusSource,
    private val preferences: AppPreferences,
    availability: SpeechAvailability.Status,
) : ViewModel() {
    val push: StateFlow<PushStatus> = pushSource.status

    val voice: StateFlow<VoiceSettingsUi> = preferences.voiceEnabled
        .map { VoiceSettingsUi(it ?: false, availability) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VoiceSettingsUi(preferences.voiceEnabled.value ?: false, availability),
        )

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setVoiceEnabled(enabled) }
    }

    /** "Register again" in the push sheet (push alerts spec §7.4). */
    fun registerAgain() = pushSource.registerAgain()

    /** The notification permission may have changed while the app was away. */
    fun refreshPush() = pushSource.refresh()
}
