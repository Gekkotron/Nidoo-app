package com.gekkotron.nidoo.feature.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.FrigateConfig
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.EnergyViewPreference
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.data.normalizeFrigateUrl
import com.gekkotron.nidoo.data.normalizeHistoryUrl
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.push.PushStatusSource
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.feature.voice.TtsAvailability
import com.gekkotron.nidoo.feature.voice.TtsSpeaker
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.voice.ShortcutMatcher
import com.gekkotron.nidoo.voice.VoiceCatalogBuilder
import com.gekkotron.nidoo.voice.VoiceLang
import com.gekkotron.nidoo.voice.VoiceLexicon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val connection: ConnectionSummary? = null,
    val push: PushStatus = PushStatus.Waiting,
    val voice: VoiceSettingsUi = VoiceSettingsUi(),
    val energyView: EnergyViewPreference = EnergyViewPreference.CLASSIC,
)

@HiltViewModel
class SettingsViewModel internal constructor(
    private val profiles: ProfileRepository,
    private val preferences: AppPreferences,
    private val push: PushStatusSource,
    private val source: EntitySource,
    availability: SpeechAvailability.Status,
    private val ttsAvailability: StateFlow<TtsAvailability> = MutableStateFlow(TtsAvailability.Unknown),
) : ViewModel() {
    @Inject
    constructor(
        profiles: ProfileRepository,
        preferences: AppPreferences,
        push: PushStatusSource,
        source: EntitySource,
        @ApplicationContext context: Context,
        tts: TtsSpeaker,
    ) : this(profiles, preferences, push, source, SpeechAvailability.check(context), tts.availability)

    private val shortcutRows = combine(source.shortcuts, source.entities, source.areas) { shortcuts, entities, areas ->
        // Broken-ness is judged against one catalog; labels use each shortcut's own language.
        val catalog = VoiceCatalogBuilder.build(VoiceLang.FR, entities.values, areas)
        ShortcutMatcher(catalog, shortcuts.values.toList()).status().map { s ->
            val sc = s.shortcut
            ShortcutRow(
                id = sc.id,
                phrase = sc.phrase,
                lang = sc.lang,
                label = "${VoiceLexicon.actionLabel(sc.lang, sc.intent.action)} (${sc.intent.entityIds.size})",
                broken = s.broken,
            )
        }
    }

    private val voiceUi = combine(preferences.voiceEnabled.map { it ?: false }, shortcutRows, ttsAvailability) { enabled, rows, tts ->
        VoiceSettingsUi(enabled, availability, rows, tts)
    }

    val state: StateFlow<SettingsUi> = combine(preferences.theme, profiles.state, push.status, voiceUi, preferences.energyView) { theme, profile, status, voice, energyView ->
        SettingsUi(theme ?: ThemePreference.SYSTEM, ConnectionSummary.from(profile), status, voice, energyView ?: EnergyViewPreference.CLASSIC)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Seeded from the live values so the radio does not show System for a frame.
        SettingsUi(
            preferences.theme.value ?: ThemePreference.SYSTEM,
            ConnectionSummary.from(profiles.state.value),
            push.status.value,
            VoiceSettingsUi(preferences.voiceEnabled.value ?: false, availability, ttsAvailability = ttsAvailability.value),
            preferences.energyView.value ?: EnergyViewPreference.CLASSIC,
        ),
    )

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setVoiceEnabled(enabled) }
    }

    fun deleteShortcut(id: String) {
        viewModelScope.launch { source.deleteShortcut(id) }
    }

    fun setEnergyView(view: EnergyViewPreference) {
        viewModelScope.launch { preferences.setEnergyView(view) }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    /** Blank clears; anything else must be a history URL (history spec §4.4). */
    fun setHistoryUrl(text: String): Boolean {
        val url = if (text.isBlank()) null else normalizeHistoryUrl(text) ?: return false
        viewModelScope.launch { profiles.setHistoryUrl(url) }
        return true
    }

    /**
     * Blank [url] removes Frigate; otherwise it must be `http(s)://host[:port]` (Frigate auth spec §3). A blank [password]
     * keeps the stored one, but only for the same user; no user means no login.
     */
    fun setFrigate(url: String, user: String, password: String): Boolean {
        if (url.isBlank()) {
            viewModelScope.launch { profiles.setFrigate(null) }
            return true
        }
        val normalized = normalizeFrigateUrl(url) ?: return false
        val username = user.trim().ifEmpty { null }
        val old = ((profiles.state.value as? ProfileState.Ready)?.profile as? ConnectionProfile.Direct)?.frigate
        val pass = when {
            username == null -> null
            password.isNotEmpty() -> password
            old?.username == username -> old.password
            else -> null
        }
        viewModelScope.launch { profiles.setFrigate(FrigateConfig(normalized, username, pass)) }
        return true
    }

    /** "Register again" in the push sheet (push alerts spec §7.4). */
    fun registerAgain() = push.registerAgain()

    /** The notification permission may have changed while the app was away. */
    fun refreshPush() = push.refresh()
}
