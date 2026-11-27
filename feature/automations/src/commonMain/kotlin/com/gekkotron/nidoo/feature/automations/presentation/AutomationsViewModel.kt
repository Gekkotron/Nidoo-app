package com.gekkotron.nidoo.feature.automations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.feature.automations.resources.Res
import com.gekkotron.nidoo.feature.automations.resources.automations_toggle_failed
import com.gekkotron.nidoo.feature.automations.resources.wake_up_hub_outdated
import com.gekkotron.nidoo.feature.automations.resources.wake_up_save_failed
import com.gekkotron.nidoo.flows.FlowDto
import com.gekkotron.nidoo.flows.FlowsHttpError
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.flows.FlowsSnapshot
import com.gekkotron.nidoo.flows.isWakeUp
import com.gekkotron.nidoo.flows.wakeUpTime
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.LinkState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

class AutomationsViewModel internal constructor(
    private val repo: FlowsRepository,
    areas: Flow<List<Area>>,
    link: Flow<LinkState>,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    constructor(repo: FlowsRepository, source: EntitySource) : this(repo, source.areas, source.link, Dispatchers.Default)

    private val selected = MutableStateFlow(ALL_KEY)
    private val toast = MutableStateFlow<StringResource?>(null)

    private val section = MutableStateFlow(RoutineSection.Flows)
    private val editor = MutableStateFlow<AlarmEditor?>(null)

    private val base: Flow<AutomationsUiState> =
        combine(repo.flows, areas, link, selected, toast) { snap, areaList, linkState, sel, toastRes ->
            build(snap, areaList, linkState !is LinkState.Connected, sel, toastRes)
        }

    val state: StateFlow<AutomationsUiState> =
        combine(base, section, editor) { ui, sec, ed ->
            // The editor closes when its alarm disappears from the hub.
            ui.copy(section = sec, editor = ed?.takeIf { e -> ui.alarms.any { it.id == e.id } })
        }
            .flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AutomationsUiState(true, false, listOf(AutomationChip.All), ALL_KEY, emptyList(), null),
            )

    fun onChipSelected(key: String) = selected.update { key }

    fun onToastShown() = toast.update { null }

    fun onSectionSelected(section: RoutineSection) = this.section.update { section }

    fun onAlarmTapped(id: String) {
        val s = state.value
        if (s.isOffline) return
        val row = s.alarms.firstOrNull { it.id == id } ?: return
        val time = row.time ?: return
        editor.value = AlarmEditor(row.id, row.name, time.hour, time.minute, time.days.toSet())
    }

    fun onEditorTime(hour: Int, minute: Int) = editor.update { e ->
        if (e == null || e.saving) e else e.copy(hour = hour, minute = minute, error = null)
    }

    fun onEditorDayToggled(day: Int) = editor.update { e ->
        if (e == null || e.saving) e else e.copy(days = if (day in e.days) e.days - day else e.days + day, error = null)
    }

    fun onEditorDismissed() = editor.update { null }

    fun onEditorSave() {
        val e = editor.value ?: return
        if (!e.canSave) return
        editor.value = e.copy(saving = true, error = null)
        viewModelScope.launch {
            repo.setWakeUp(e.id, e.hour, e.minute, e.days.sorted()).fold(
                onSuccess = { editor.update { cur -> if (cur?.id == e.id) null else cur } },
                onFailure = { err ->
                    val res = if (err is FlowsHttpError && err.status == 404) Res.string.wake_up_hub_outdated else Res.string.wake_up_save_failed
                    editor.update { cur -> if (cur?.id == e.id) cur.copy(saving = false, error = res) else cur }
                },
            )
        }
    }

    fun onToggle(id: String, enabled: Boolean) {
        if (state.value.isOffline || id in repo.flows.value.inFlight) return
        viewModelScope.launch {
            repo.setEnabled(id, enabled).onFailure { toast.value = Res.string.automations_toggle_failed }
        }
    }

    private fun build(
        snap: FlowsSnapshot,
        areaList: List<Area>,
        offline: Boolean,
        sel: String,
        toastRes: StringResource?,
    ): AutomationsUiState {
        val plain = snap.flows.filterNot { it.isWakeUp }
        val rooms = areaList.filter { it.id != UNASSIGNED_AREA }
        val byId = rooms.withIndex().associate { (i, a) -> a.id to (i to a) }
        // A flow is unassigned when its area is null, blank, or not one of the source's rooms.
        fun roomOf(area: String?) = area?.takeIf { it.isNotBlank() }?.let { byId[it] }
        val hasUnassigned = plain.any { roomOf(it.area) == null }
        val chips = buildList {
            add(AutomationChip.All)
            // Only rooms with at least one flow, like Home only shows rooms in use.
            val used = plain.mapNotNull { roomOf(it.area)?.second?.id }.toSet()
            rooms.filter { it.id in used }.forEach { add(AutomationChip.Room(it.id, it.name)) }
            if (hasUnassigned) add(AutomationChip.Unassigned)
        }
        val selectedKey = if (chips.any { it.key == sel }) sel else ALL_KEY

        fun row(f: FlowDto, label: AreaLabel?) = AutomationRow(
            id = f.id,
            name = f.name,
            area = label,
            enabled = f.enabled,
            toggleEnabled = !offline && f.id !in snap.inFlight,
        )

        val rows = when (selectedKey) {
            ALL_KEY -> plain
                .sortedWith(
                    compareBy<FlowDto> { roomOf(it.area)?.first ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.id },
                )
                .map { f -> row(f, roomOf(f.area)?.let { AreaLabel.Named(it.second.name) } ?: AreaLabel.Unassigned) }
            UNASSIGNED_AREA -> plain.filter { roomOf(it.area) == null }.map { row(it, null) }
            else -> plain.filter { roomOf(it.area)?.second?.id == selectedKey }.map { row(it, null) }
        }
        val alarms = snap.flows.filter { it.isWakeUp }
            .map { f ->
                AlarmRow(f.id, f.name, f.wakeUpTime(), f.enabled, toggleEnabled = !offline && f.id !in snap.inFlight)
            }
            .sortedWith(
                compareBy<AlarmRow> { it.time == null }
                    .thenBy { it.time?.hour ?: 0 }
                    .thenBy { it.time?.minute ?: 0 }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id },
            )
        return AutomationsUiState(
            isLoading = !offline && !snap.isSettled,
            isOffline = offline,
            chips = chips,
            selectedChipKey = selectedKey,
            rows = rows,
            toast = toastRes,
            alarms = alarms,
        )
    }
}
