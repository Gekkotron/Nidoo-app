package com.gekkotron.nidoo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.feature.voice.VoicePermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The common Voice page (iOS; Android keeps its own in `AndroidSettingsViewModel`): turning voice on asks for permission first. */
class VoiceSettingsViewModel(
    private val preferences: AppPreferences,
    private val availability: () -> SpeechAvailability.Status,
    private val permissions: VoicePermissions,
) : ViewModel() {
    /** The latest availability: read at construction and again after a granted request (it can change once iOS has asked). */
    private val status = MutableStateFlow(availability())

    val voice: StateFlow<VoiceSettingsUi> = combine(preferences.voiceEnabled, status) { enabled, availability ->
        VoiceSettingsUi(enabled ?: false, availability)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VoiceSettingsUi(preferences.voiceEnabled.value ?: false, status.value),
    )

    private val _denied = MutableStateFlow(false)

    /** True after a refusal, until the page has shown its snackbar ([deniedShown]). */
    val denied: StateFlow<Boolean> = _denied.asStateFlow()

    fun setVoiceEnabled(on: Boolean) {
        viewModelScope.launch {
            if (on && !permissions.request()) {
                preferences.setVoiceEnabled(false)
                _denied.value = true
            } else if (on && availability().also { status.value = it } != SpeechAvailability.Status.Ready) {
                // Granted, but the phone still can't listen on-device: stay off, the page says why.
                preferences.setVoiceEnabled(false)
            } else {
                preferences.setVoiceEnabled(on)
            }
        }
    }

    fun deniedShown() {
        _denied.value = false
    }
}
