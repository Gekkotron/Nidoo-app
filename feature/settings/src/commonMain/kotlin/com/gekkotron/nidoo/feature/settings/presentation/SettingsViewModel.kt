package com.gekkotron.nidoo.feature.settings.presentation

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
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.source.EntitySource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUi(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val connection: ConnectionSummary? = null,
    val energyView: EnergyViewPreference = EnergyViewPreference.CLASSIC,
    /** Broker link, for the Settings header and the Connection row. */
    val link: LinkState = LinkState.Connecting,
    /** Rooms the house has, Unassigned not counted (the Rooms row's status). */
    val roomCount: Int = 0,
    /** The rows only this platform has, after Rooms in the House group. */
    val platformRows: List<PlatformRow> = emptyList(),
)

/** What only the Settings list shows. */
private data class ListExtras(val link: LinkState, val roomCount: Int)

private fun roomCount(areas: List<Area>) = areas.count { it.id != UNASSIGNED_AREA }

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val preferences: AppPreferences,
    private val source: EntitySource,
    private val platform: PlatformSettings,
) : ViewModel() {
    private val listExtras = combine(source.link, source.areas) { link, areas -> ListExtras(link, roomCount(areas)) }

    val state: StateFlow<SettingsUi> = combine(
        combine(preferences.theme, profiles.state, preferences.energyView, platform.rows) { theme, profile, energyView, rows ->
            SettingsUi(theme ?: ThemePreference.SYSTEM, ConnectionSummary.from(profile), energyView ?: EnergyViewPreference.CLASSIC, platformRows = rows)
        },
        listExtras,
    ) { ui, extras -> ui.copy(link = extras.link, roomCount = extras.roomCount) }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Seeded from the live values so the radio does not show System for a frame.
        SettingsUi(
            preferences.theme.value ?: ThemePreference.SYSTEM,
            ConnectionSummary.from(profiles.state.value),
            preferences.energyView.value ?: EnergyViewPreference.CLASSIC,
            source.link.value,
            roomCount(source.areas.value),
            platform.currentRows(),
        ),
    )

    /** The list is shown again: let the platform re-read what may have changed while the app was away. */
    fun refresh() = platform.refresh()

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
}
