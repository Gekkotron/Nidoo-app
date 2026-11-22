package com.gekkotron.nidoo.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.designsystem.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The iOS app's root state (the Android `AppViewModel` without push, links or presence): the saved profile picks the
 * screen, the stored theme styles it, and "Set up again" returns to onboarding.
 */
class AppShellViewModel(
    private val profiles: ProfileRepository,
    preferences: AppPreferences,
    private val sources: EntitySourceSession,
) : ViewModel() {
    val profile: StateFlow<ProfileState> = profiles.state

    /** The theme picked on this phone; null until the store has loaded (settings spec §3.2). */
    val theme: StateFlow<ThemeChoice?> = preferences.theme
        .map { it?.toChoice() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.theme.value?.toChoice())

    fun finishRooms() {
        viewModelScope.launch { profiles.finishRooms() }
    }

    /** iOS has no push (spec §3), so the notification step is closed without being shown. */
    fun finishNotifications() {
        viewModelScope.launch { profiles.finishNotifications() }
    }

    /**
     * Forgets the profile. The transport closes, the discovery source is dropped (the next profile may be an equal one,
     * such as the demo again, and must start from an empty house), and the app returns to onboarding.
     */
    fun setUpAgain() {
        viewModelScope.launch {
            profiles.clear()
            sources.reset()
        }
    }
}
