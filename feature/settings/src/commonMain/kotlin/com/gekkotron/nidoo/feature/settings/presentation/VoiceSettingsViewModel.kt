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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The common Voice page (iOS; Android keeps its own in `AndroidSettingsViewModel`): turning voice on asks for permission first. */
class VoiceSettingsViewModel(
    private val preferences: AppPreferences,
    availability: SpeechAvailability.Status,
    private val permissions: VoicePermissions,
) : ViewModel() {
    val voice: StateFlow<VoiceSettingsUi> = preferences.voiceEnabled
        .map { VoiceSettingsUi(it ?: false, availability) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VoiceSettingsUi(preferences.voiceEnabled.value ?: false, availability),
        )

    private val _denied = MutableStateFlow(false)

    /** True after a refusal, until the page has shown its snackbar ([deniedShown]). */
    val denied: StateFlow<Boolean> = _denied.asStateFlow()

    fun setVoiceEnabled(on: Boolean) {
        viewModelScope.launch {
            if (on && !permissions.request()) {
                preferences.setVoiceEnabled(false)
                _denied.value = true
            } else {
                preferences.setVoiceEnabled(on)
            }
        }
    }

    fun deniedShown() {
        _denied.value = false
    }
}
