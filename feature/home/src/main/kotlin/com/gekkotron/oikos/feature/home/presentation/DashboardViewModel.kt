package com.gekkotron.oikos.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.oikos.dashboards.Dashboard
import com.gekkotron.oikos.dashboards.DashboardsRepository
import com.gekkotron.oikos.dashboards.HOUSE_DASHBOARD_ID
import com.gekkotron.oikos.dashboards.WidgetKind
import com.gekkotron.oikos.dashboards.WidgetRange
import com.gekkotron.oikos.data.AppPreferences
import com.gekkotron.oikos.data.DashboardChoice
import com.gekkotron.oikos.feature.energy.presentation.EnergyStats
import com.gekkotron.oikos.flows.FlowsHttpError
import com.gekkotron.oikos.flows.FlowsRepository
import com.gekkotron.oikos.history.HistoryClient
import com.gekkotron.oikos.history.HistoryPoint
import com.gekkotron.oikos.history.HistoryRefs
import com.gekkotron.oikos.history.HistoryResult
import com.gekkotron.oikos.source.EntitySource
import com.gekkotron.oikos.transport.LinkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel internal constructor(
    private val source: EntitySource,
    private val dashboards: DashboardsRepository,
    private val flows: FlowsRepository,
    private val preferences: AppPreferences,
    private val history: HistoryClient,
    private val labels: LabelText,
    private val dispatcher: CoroutineDispatcher,
    private val refresh: Duration = 5.minutes,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        source: EntitySource,
        dashboards: DashboardsRepository,
        flows: FlowsRepository,
        preferences: AppPreferences,
        history: HistoryClient,
        labels: LabelText,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(source, dashboards, flows, preferences, history, labels, dispatcher, 5.minutes, System::currentTimeMillis)

    /** What the shown dashboard needs fetched: energy windows (days) and history series (entity, range). */
    private data class Needs(val days: Set<Int> = emptySet(), val series: Set<Pair<String, WidgetRange>> = emptySet())

    private data class Fetched(val kwhByDays: Map<Int, Double> = emptyMap(), val series: Map<Pair<String, WidgetRange>, List<HistoryPoint>> = emptyMap())

    private class Selected(val choice: DashboardChoice?, val snapshotDashboards: List<Dashboard>, val resolution: Resolution)

    private val running = MutableStateFlow<Set<String>>(emptySet())
    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    private val selection: Flow<Selected> = combine(preferences.selectedDashboard, dashboards.dashboards) { choice, snap ->
        Selected(choice, snap.dashboards, DashboardSelection.resolve(choice, snap))
    }

    private val houseData: Flow<HouseData> = combine(
        source.entities,
        source.states(),
        source.areas,
        combine(source.link, history.available, source.hubGroups, ::Triple),
    ) { entities, states, areas, (link, historyOn, hubGroups) ->
        HouseData(entities, states, areas, link, hubGroups, historyOn)
    }

    private fun entriesOf(sel: Selected) =
        listOf(SwitcherEntry(HOUSE_DASHBOARD_ID, null)) + sel.snapshotDashboards.map { SwitcherEntry(it.id, it.name) }

    /** The state for [sel] before any House data or fetched figure is in: House, or waiting for the dashboard. */
    private fun waiting(sel: Selected): DashboardScreenState {
        val (view, id) = when (val v = sel.resolution.view) {
            ResolvedView.House -> DashboardView.House to HOUSE_DASHBOARD_ID
            is ResolvedView.Loading -> DashboardView.Loading(v.name) to (sel.choice?.id ?: HOUSE_DASHBOARD_ID)
            is ResolvedView.Shown -> DashboardView.Loading(v.dashboard.name) to v.dashboard.id
        }
        return DashboardScreenState(sel.resolution.switcherVisible, entriesOf(sel), id, view)
    }

    /** What is fetched and whether it can be: a change of this key (and only that) restarts the fetch loop. */
    private data class FetchKey(val needs: Needs, val linkUp: Boolean, val historyOn: Boolean, val entitiesLoaded: Boolean, val present: Set<String>)

    private val fetched: StateFlow<Fetched> = combine(
        selection.map { sel -> (sel.resolution.view as? ResolvedView.Shown)?.dashboard?.let(::needsOf) ?: Needs() },
        source.link,
        history.available,
        source.entities,
    ) { needs, link, historyOn, entities ->
        FetchKey(needs, link is LinkState.Connected, historyOn, entities.isNotEmpty(), needs.series.map { it.first }.filterTo(mutableSetOf()) { it in entities })
    }.distinctUntilChanged().flatMapLatest { key ->
        when {
            key.needs == Needs() || !key.linkUp || !key.historyOn || !key.entitiesLoaded -> flowOf(Fetched())
            else -> flow {
                while (true) {
                    emit(fetch(key.needs))
                    delay(refresh)
                }
            }
        }
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Fetched())

    val state: StateFlow<DashboardScreenState> = selection.flatMapLatest { sel ->
        when (val view = sel.resolution.view) {
            ResolvedView.House, is ResolvedView.Loading -> flowOf(waiting(sel))
            is ResolvedView.Shown -> {
                val dashboard = view.dashboard
                combine(houseData, fetched, flows.flows, running) { house, fetchedNow, flowSnap, runningNow ->
                    val live = if (dashboard.widgets.any { it.kind == WidgetKind.ENERGY_STAT }) {
                        EnergyStats.live(house.entities, house.states, house.link, clock())
                    } else {
                        null
                    }
                    val extras = TileExtras(flowSnap.flows, EnergyFigures(fetchedNow.kwhByDays, live), fetchedNow.series)
                    val ui = DashboardUiBuilder.build(dashboard, house, extras, Locale.getDefault(), labels, clock())
                    DashboardScreenState(true, entriesOf(sel), dashboard.id, DashboardView.Shown(ui), runningNow)
                }
            }
        }
    }.conflate().flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    private fun initialState(): DashboardScreenState {
        val choice = preferences.selectedDashboard.value
        val snap = dashboards.dashboards.value
        return waiting(Selected(choice, snap.dashboards, DashboardSelection.resolve(choice, snap)))
    }

    private fun needsOf(d: Dashboard): Needs {
        val days = d.widgets.filter { it.kind == WidgetKind.ENERGY_STAT }.mapNotNull { EnergyMetric.of(it.config.metric)?.days }.toSet()
        val series = d.widgets.filter { it.kind == WidgetKind.HISTORY_CHART }.mapNotNull { w ->
            val id = w.config.entityId
            val range = w.config.range
            if (id != null && range != null && range != WidgetRange.UNKNOWN) id to range else null
        }.toSet()
        return Needs(days, series)
    }

    /** An unexpected failure is "unknown" (null), not the end of the state pipeline. */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun fetch(needs: Needs): Fetched = coroutineScope {
        val entities = source.entities.value
        val states = source.states().first()
        val link = source.link.value
        val now = clock()
        val kwh = needs.days.map { days ->
            async { safely { EnergyStats.consumption(entities, states, link, now, history, days) }?.let { days to it } }
        }
        val series = needs.series.map { (id, range) ->
            async {
                val ref = entities[id]?.let(HistoryRefs::of) ?: return@async null
                (safely { history.series(ref, DashboardUiBuilder.historyRange(range)) } as? HistoryResult.Ok)?.let { (id to range) to it.value.points }
            }
        }
        Fetched(kwh.awaitAll().filterNotNull().toMap(), series.awaitAll().filterNotNull().toMap())
    }

    init {
        // Keep the stored choice honest: a deleted dashboard goes back to House, a rename is followed.
        viewModelScope.launch {
            selection.collect { sel ->
                val stored = sel.choice ?: return@collect
                if (sel.resolution.resetToHouse) {
                    preferences.replaceSelectedDashboard(stored.id, HOUSE_DASHBOARD_ID, null)
                    return@collect
                }
                val shown = (sel.resolution.view as? ResolvedView.Shown)?.dashboard ?: return@collect
                if (stored.name != shown.name) preferences.replaceSelectedDashboard(stored.id, shown.id, shown.name)
            }
        }
    }

    /** Saves the choice with the dashboard's current name (null for House). */
    fun select(id: String) {
        viewModelScope.launch {
            val name = if (id == HOUSE_DASHBOARD_ID) null else dashboards.dashboards.value.dashboards.firstOrNull { it.id == id }?.name
            preferences.setSelectedDashboard(id, name)
        }
    }

    fun runFlow(id: String) {
        if (id in running.value) return
        if (source.link.value !is LinkState.Connected) {
            viewModelScope.launch { _events.send(DashboardEvent.FlowRunFailed(refused = false)) }
            return
        }
        running.update { it + id }
        viewModelScope.launch {
            try {
                flows.run(id).onFailure { _events.send(DashboardEvent.FlowRunFailed(refused = (it as? FlowsHttpError)?.status == 422)) }
            } finally {
                running.update { it - id }
            }
        }
    }
}
