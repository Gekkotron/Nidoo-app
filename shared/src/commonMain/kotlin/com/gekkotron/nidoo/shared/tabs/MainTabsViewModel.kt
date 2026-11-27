package com.gekkotron.nidoo.shared.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.model.AlarmState
import com.gekkotron.nidoo.model.EnergyRole
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.PowerUnits
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.source.EntitySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Live decorations for the tab bar: an armed shield, a producing bolt (nav icon states §A). */
data class TabStatus(val alarmArmed: Boolean = false, val solarActive: Boolean = false)

/**
 * [source] is asked again whenever the profile changes (a new profile, or "set up again", which passes through
 * `None`), because each profile has its own `EntitySource`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainTabsViewModel(profiles: Flow<ProfileState>, source: () -> EntitySource) : ViewModel() {
    val status: StateFlow<TabStatus> = profiles
        .map { (it as? ProfileState.Ready)?.profile }
        .distinctUntilChanged()
        .flatMapLatest { profile ->
            // No profile (loading, none, locked): nothing to watch, and no source to build.
            if (profile == null) return@flatMapLatest flowOf(TabStatus())
            val s = source()
            combine(s.entities, s.states()) { entities, states ->
                TabStatus(alarmArmed = alarmArmed(entities, states), solarActive = solarActive(entities, states))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabStatus())
}

/** Any alarm panel in an active armed/triggered state lights the shield. */
internal fun alarmArmed(entities: Map<String, Entity>, states: Map<String, EntityState>): Boolean =
    entities.values.any { e ->
        e.kind == Kind.ALARM_PANEL &&
            ((states[e.uniqueId]?.value as? StateValue.Alarm)?.state in ACTIVE_ALARM_STATES)
    }

internal val ACTIVE_ALARM_STATES: Set<AlarmState> = setOf(
    AlarmState.ARMED_HOME, AlarmState.ARMED_AWAY, AlarmState.ARMED_NIGHT,
    AlarmState.ARMED_VACATION, AlarmState.ARMED_CUSTOM_BYPASS,
    AlarmState.ARMING, AlarmState.PENDING, AlarmState.TRIGGERED,
)

/** The IDLE threshold EnergyFlowBuilder uses to call the house "idle"; small enough to light up
 *  as soon as a panel starts producing but not from inverter standby noise. */
internal const val SOLAR_IDLE_W: Double = 10.0

internal fun solarActive(entities: Map<String, Entity>, states: Map<String, EntityState>): Boolean {
    val solars = entities.values.filter { it.ui.energyRole == EnergyRole.SOLAR }
    if (solars.isEmpty()) return false
    val total = solars.sumOf { e ->
        val numeric = (states[e.uniqueId]?.value as? StateValue.Numeric)?.value ?: return@sumOf 0.0
        val factor = PowerUnits.factor(e.unit) ?: return@sumOf 0.0
        val signed = numeric * factor
        if (signed > 0.0) signed else 0.0
    }
    return total > SOLAR_IDLE_W
}
