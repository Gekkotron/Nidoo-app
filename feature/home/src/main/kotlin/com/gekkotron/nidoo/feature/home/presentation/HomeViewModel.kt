package com.gekkotron.nidoo.feature.home.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gekkotron.nidoo.climate.ClimateCommands
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.climate.ClimateTopics
import com.gekkotron.nidoo.transport.MqttTransport
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.model.SwitchType
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.source.EntitySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.gekkotron.nidoo.model.Kind
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.gekkotron.nidoo.format.systemLocale
import kotlin.time.Clock
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val source: EntitySource,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val labels: LabelText = LabelText { null },
    private val history: HistoryClient = HistoryClient.None,
    preferences: AppPreferences,
    private val climate: ClimateRepository = ClimateRepository(),
    /** Sends the Climat hold commands; null in previews and tests that never boost. */
    private val transport: MqttTransport? = null,
) : ViewModel() {
    /** Whether the opt-in voice assistant is on; the mic button shows only then. */
    val voiceEnabled: StateFlow<Boolean> = preferences.voiceEnabled
        .map { it ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // conflate() collapses a burst of state messages to one rebuild per collection turn instead of one
    // per message, and flowOn moves the combine (and HomeUiBuilder.build) off Main; the final value is
    // never dropped.
    private val home = combine(
        source.entities,
        source.states(),
        source.areas,
        combine(savedState.getStateFlow(KEY_FILTER, RoomFilter.Favorites.key), savedState.getStateFlow(KEY_QUERY, ""), ::Pair),
        combine(source.link, history.available, source.hubGroups, ::Triple),
    ) { entities, states, areas, (key, query), linkHistoryHub ->
        val (link, historyOn, hubGroups) = linkHistoryHub
        val ui = HomeUiBuilder.build(entities, states, areas, RoomFilter.fromKey(key), link, systemLocale(), labels, query, historyOn, hubGroups)
        // The selected chip may no longer exist (its last card was unhidden, its area emptied…); build()
        // already falls back to All for this state, but the saved key must follow, or a stale "hidden"
        // key comes back to life and jumps the screen back there as soon as something is hidden again.
        // Favorites is the exception: it is the launch default, and on a cold start the retained
        // favourite flags land after the entities, so its chip is briefly missing. Keeping the key
        // lets the screen settle on Favorites once they arrive instead of sticking on All.
        // Not while the source is still empty: after process death the restored key (a room, Rooms)
        // has no chip yet either, and rewriting it would land the user on All devices for good.
        if (!ui.loading && ui.selected.key != key && key != RoomFilter.Favorites.key) savedState[KEY_FILTER] = ui.selected.key
        ui
    }

    /** Wall-clock seconds, every [CLIMAT_TICK_MS], so a boost's end and expiry stay right on screen. */
    private val climatClock = flow {
        while (true) {
            emit(Clock.System.now().epochSeconds)
            delay(CLIMAT_TICK_MS)
        }
    }

    private data class ClimatSend(val sending: Boolean = false, val error: Boolean = false)

    private val climatSend = MutableStateFlow(ClimatSend())
    private val _climatResults = Channel<ClimatResult>(Channel.BUFFERED)

    /** One result per boost or cancel; the screen acts on it once (state edges can be conflated away). */
    val climatResults: Flow<ClimatResult> = _climatResults.receiveAsFlow()
    private var climatJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(home, source.entities, climate.zones, climatClock, climatSend) { ui, entities, zones, now, send ->
        val room = ui.selected as? RoomFilter.Area
        ui.copy(
            climat = room?.let { climatBannerFor(it.id, zones.values, entities, now) },
            climatSending = send.sending,
            climatError = send.error,
        )
    }.conflate().flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** Member id → the group send it last took part in; a member's own send removes it. */
    private val groupSends = mutableMapOf<String, GroupSend>()
    /** Keyed by group and command: failures of two different sends never share a snackbar or a Retry. */
    private val failedInGroup = mutableMapOf<Pair<String, Command>, MutableList<String>>()

    /** Last group card a dashboard handed to [sendGroup], so a Retry (which carries no card) still finds it. */
    private val lastGroupCards = mutableMapOf<String, CardUi>()

    private data class GroupSend(val groupId: String, val kind: Kind, val command: Command, val names: Map<String, String>)

    /** Device id → the whole-room send it last took part in; the device's own send removes it. */
    private val roomSends = mutableMapOf<String, RoomSend>()
    /** Keyed by the room send id, not the command: a lights-on tap sends each light its own command, and they still share a snackbar. */
    private val failedInRoom = mutableMapOf<String, LinkedHashMap<String, RoomSend>>()

    private data class RoomSend(val sendId: String, val areaId: String, val kind: RoomKind, val command: Command, val name: String)

    init {
        viewModelScope.launch {
            source.commandFailures.collect { failure ->
                val roomSend = roomSends[failure.uniqueId]?.takeIf { it.command == failure.command }
                if (roomSend != null) {
                    roomSends.remove(failure.uniqueId)
                    val bucket = failedInRoom.getOrPut(roomSend.sendId) { LinkedHashMap() }
                    bucket[failure.uniqueId] = roomSend
                    // The first failure opens a 1 s window; the rest of the same tap joins it.
                    if (bucket.size == 1) {
                        launch {
                            delay(GROUP_FAILURE_WINDOW_MS)
                            val failed = failedInRoom.remove(roomSend.sendId).orEmpty()
                            _events.send(
                                HomeEvent.RoomNotConfirmed(
                                    roomSend.sendId, roomSend.areaId, roomSend.kind,
                                    failed.mapValues { it.value.command }, failed.values.map { it.name },
                                ),
                            )
                        }
                    }
                    return@collect
                }
                val send = groupSends[failure.uniqueId]?.takeIf { it.command == failure.command }
                if (send == null) return@collect _events.send(HomeEvent.NotConfirmed(failure.uniqueId, failure.command))
                groupSends.remove(failure.uniqueId)
                val key = send.groupId to send.command
                val bucket = failedInGroup.getOrPut(key) { mutableListOf() }
                bucket += failure.uniqueId
                // The first failure opens a 1 s window; the rest of the same send joins it.
                if (bucket.size == 1) {
                    launch {
                        delay(GROUP_FAILURE_WINDOW_MS)
                        val ids = failedInGroup.remove(key).orEmpty()
                        _events.send(HomeEvent.GroupNotConfirmed(send.groupId, send.kind, ids, ids.map { send.names[it] ?: it }, send.command))
                    }
                }
            }
        }
    }

    private val editor = OverrideEditor(source)

    /** Saves the override sheet through [OverrideEditor.customize]; a failure is a [HomeEvent.SaveFailed]. */
    fun customize(id: String, newRooms: List<Area>, override: UiOverride) {
        viewModelScope.launch { if (!editor.customize(id, newRooms, override)) _events.send(HomeEvent.SaveFailed) }
    }

    /** A hub group's name and Favorites pin live on the group entity's own override. A failed write is a [HomeEvent.SaveFailed]. */
    fun customizeGroup(groupId: String, name: String, favorite: Boolean, group: CardUi? = null) {
        viewModelScope.launch {
            val target = (state.value.card(groupId) ?: group ?: lastGroupCards[groupId])?.takeIf { it.isHubGroup } ?: return@launch
            val clean = name.trim().takeIf { it.isNotEmpty() }
            val saved = editor.save(groupId, target.ui.copy(name = clean, favorite = favorite))
            if (!saved) _events.send(HomeEvent.SaveFailed)
        }
    }

    /** Boosts Climat zone [zone] to [setpoint] °C for [minutes] (spec §6). */
    fun startBoost(zone: String, setpoint: Double, minutes: Int) = sendHold(zone, ClimateCommands.boost(setpoint, minutes))

    /** Ends the zone's boost (or whole-zone hold); holds made on the devices stay. */
    fun cancelBoost(zone: String) = sendHold(zone, ClimateCommands.CANCEL)

    fun dismissClimatError() = climatSend.update { it.copy(error = false) }

    /**
     * Publishes the hold command, then waits up to [BOOST_CONFIRM_MS] for the hub to republish the
     * zone's overrides; no answer (or a failed publish) is [HomeUiState.climatError].
     */
    private fun sendHold(zone: String, payload: String) {
        val transport = transport ?: return
        climatJob?.cancel()
        val before = climate.zones.value[zone]?.overrides
        climatSend.value = ClimatSend(sending = true)
        climatJob = viewModelScope.launch {
            try {
                transport.publish(ClimateTopics.holdCommand(zone), payload.encodeToByteArray(), qos = 1, retain = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                climatSend.value = ClimatSend(error = true)
                _climatResults.send(ClimatResult.REFUSED)
                return@launch
            }
            val answered = withTimeoutOrNull(BOOST_CONFIRM_MS) { climate.zones.first { it[zone]?.overrides != before } }
            climatSend.value = ClimatSend(error = answered == null)
            _climatResults.send(if (answered == null) ClimatResult.REFUSED else ClimatResult.CONFIRMED)
        }
    }

    /** Home search; blank ends it. */
    fun search(query: String) {
        savedState[KEY_QUERY] = query
    }

    /** Switching view ends a search (rooms design §6.5). */
    fun select(filter: RoomFilter) {
        savedState[KEY_QUERY] = ""
        savedState[KEY_FILTER] = filter.key
    }

    fun tap(card: CardUi) {
        when (card.tap) {
            TapAction.TOGGLE -> when {
                card.isGroup -> sendGroup(card.id, GroupCommands.toggle(card), group = card)
                card.kind == Kind.SWITCH -> dispatchSwitchTap(card)
                // Absolute, so "Retry" after a late confirmation repeats the intent instead of flipping it back.
                else -> send(card.id, if (card.on) Command.TurnOff else turnOnFor(card), card)
            }
            TapAction.PRESS -> send(card.id, Command.Press, card)
            TapAction.OPEN_SHEET, TapAction.OPEN_HISTORY, TapAction.NONE -> Unit
        }
    }

    /** A `Kind.SWITCH` card's tap goes through [cardTap], so Door opener / Siren / Chime speak their own command. */
    private fun dispatchSwitchTap(card: CardUi) {
        val type = SwitchType.of(card.protocolKind, card.ui)
        when (val tap = cardTap(type, card.on, card.ui.securityAsk)) {
            is CardTap.Send -> send(card.id, tap.command, card)
            is CardTap.Confirm -> viewModelScope.launch {
                _events.send(HomeEvent.AskBeforeAction(card.id, tap.labelResId, tap.command))
            }
            CardTap.None -> Unit
        }
    }

    private fun turnOnFor(card: CardUi): Command.TurnOn =
        if (card.kind == Kind.LIGHT) lightOnCommand(card.ui, card.capabilities) else Command.TurnOn()

    fun send(id: String, command: Command, card: CardUi? = null) {
        viewModelScope.launch {
            groupSends.remove(id)
            roomSends.remove(id)
            // A cached value may be outdated: never act on it (entity cache spec §3.6).
            if ((state.value.card(id) ?: card)?.stale == true) return@launch _events.send(HomeEvent.WaitingForConnection)
            val result = source.send(id, command)
            if (result is CommandResult.Rejected) _events.send(HomeEvent.Rejected(id, result.reason))
        }
    }

    /** Room groups spec §5: one command to every capable, available member, in parallel. [only] narrows it (Retry). */
    fun sendGroup(groupId: String, command: Command, only: List<String>? = null, group: CardUi? = null) {
        viewModelScope.launch {
            if (group != null && group.isGroup) lastGroupCards[groupId] = group
            val target = (state.value.card(groupId) ?: group ?: lastGroupCards[groupId])?.takeIf { it.isGroup } ?: return@launch
            if (target.members.any { it.stale }) return@launch _events.send(HomeEvent.WaitingForConnection)
            val ids = GroupCommands.targets(target).filter { only == null || it in only }
            // Hub cover groups spec §2.4: a hub group fails under its own id, so the snackbar reads the
            // group's name instead of a bare "group_xyz".
            val names = mapOf(target.id to target.name)
            ids.forEach {
                roomSends.remove(it)
                groupSends[it] = GroupSend(groupId, target.kind, command, names)
            }
            val results = ids.map { id -> async { source.send(id, command) } }.awaitAll()
            results.firstOrNull { it is CommandResult.Rejected }?.let {
                _events.send(HomeEvent.Rejected(groupId, (it as CommandResult.Rejected).reason))
            }
        }
    }

    /**
     * A room card's lights or shutters button (rooms design §6.3): one absolute command to every
     * device [RoomCommands] names, in parallel. Nothing is sent while a receiver's value is stale.
     */
    fun sendRoom(areaId: String, kind: RoomKind) {
        val room = state.value.roomCards.firstOrNull { it.areaId == areaId && it.buttons } ?: return
        if (RoomCommands.blockedByStale(room, kind)) {
            viewModelScope.launch { _events.send(HomeEvent.WaitingForConnection) }
            return
        }
        val names = (room.lights + room.shutters).associate { it.id to it.name }
        dispatchRoom(RoomCommands.sendId(areaId, kind), areaId, kind, RoomCommands.commands(room, kind), names)
    }

    /** Retry of a [HomeEvent.RoomNotConfirmed]: the failed devices only, each with the command it was sent. */
    fun retryRoom(event: HomeEvent.RoomNotConfirmed) =
        dispatchRoom(event.sendId, event.areaId, event.kind, event.failed, event.failed.keys.zip(event.failedNames).toMap())

    private fun dispatchRoom(sendId: String, areaId: String, kind: RoomKind, commands: Map<String, Command>, names: Map<String, String>) {
        if (commands.isEmpty()) return
        viewModelScope.launch {
            commands.forEach { (id, command) ->
                groupSends.remove(id)
                roomSends[id] = RoomSend(sendId, areaId, kind, command, names[id] ?: id)
            }
            val results = commands.map { (id, command) -> async { source.send(id, command) } }.awaitAll()
            results.firstOrNull { it is CommandResult.Rejected }?.let {
                _events.send(HomeEvent.Rejected(sendId, (it as CommandResult.Rejected).reason))
            }
        }
    }

    private companion object {
        const val GROUP_FAILURE_WINDOW_MS = 1_000L
        const val CLIMAT_TICK_MS = 30_000L
        const val BOOST_CONFIRM_MS = 5_000L
        const val KEY_FILTER = "home.filter"
        const val KEY_QUERY = "home.query"
    }
}
