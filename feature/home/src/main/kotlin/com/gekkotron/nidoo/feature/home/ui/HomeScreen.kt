package com.gekkotron.nidoo.feature.home.ui

import com.gekkotron.nidoo.feature.home.presentation.ClimatResult
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.ChipItem
import com.gekkotron.nidoo.designsystem.components.LinkPill
import com.gekkotron.nidoo.designsystem.components.LinkSheet
import com.gekkotron.nidoo.designsystem.components.RoomChips
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.dashboards.HOUSE_DASHBOARD_ID
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.DashboardEvent
import com.gekkotron.nidoo.feature.home.presentation.DashboardScreenState
import com.gekkotron.nidoo.feature.home.presentation.DashboardView
import com.gekkotron.nidoo.feature.home.presentation.DashboardViewModel
import com.gekkotron.nidoo.feature.home.presentation.HomeEvent
import com.gekkotron.nidoo.feature.home.presentation.HomeUiState
import com.gekkotron.nidoo.feature.home.presentation.HomeViewModel
import com.gekkotron.nidoo.feature.home.presentation.HomeSection
import com.gekkotron.nidoo.feature.home.presentation.RoomCardUi
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import com.gekkotron.nidoo.feature.home.presentation.RoomKind
import com.gekkotron.nidoo.feature.home.presentation.TapAction
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.transport.Route
import java.time.LocalTime

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit = {},
    /** The link sheet's "Connection settings": straight to the Connection page. */
    onOpenConnectionSettings: () -> Unit = onOpenSettings,
    route: Route? = null,
    onRouteChange: (Route) -> Unit = {},
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    onOpenHistory: (String) -> Unit = {},
    onOpenEnergy: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    // Always collected, whichever view is shown: the commands look cards up in this state first.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard by dashboardViewModel.state.collectAsStateWithLifecycle()
    fun cardOf(id: String): CardUi? = state.card(id) ?: dashboard.card(id)
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var ask by rememberSaveable { mutableStateOf<HomeEvent.AskBeforeAction?>(null) }
    val unassigned = stringResource(R.string.area_unassigned)
    HomeEventsEffect(
        viewModel.events, snackbar,
        onRetry = { id, c -> viewModel.send(id, c, cardOf(id)) },
        onRetryGroup = { g, c, ids -> viewModel.sendGroup(g, c, ids, cardOf(g)) },
        onAsk = { ask = it },
        onRetryRoom = viewModel::retryRoom,
        roomName = { area -> state.roomCards.firstOrNull { it.areaId == area }?.name ?: unassigned },
    )
    val flowRefused = stringResource(R.string.widget_flow_refused)
    val flowFailed = stringResource(R.string.widget_flow_failed)
    LaunchedEffect(dashboardViewModel, snackbar) {
        dashboardViewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.FlowRunFailed -> snackbar.showSnackbar(if (event.refused) flowRefused else flowFailed)
            }
        }
    }
    val askRow = ask?.let { event -> cardOf(event.id) }
    // The entity may have left Home while the dialog was queued (removed, hidden, kind changed): close silently.
    if (ask != null && askRow == null) LaunchedEffect(ask) { ask = null }
    val currentAsk = ask
    if (currentAsk != null && askRow != null) {
        val open = currentAsk.labelResId == R.string.card_confirm_open
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(stringResource(currentAsk.labelResId, askRow.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = currentAsk.id
                    val cmd = currentAsk.command
                    ask = null
                    viewModel.send(id, cmd, askRow)
                }) {
                    Text(stringResource(if (open) R.string.card_action_open else R.string.card_action_ring))
                }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(stringResource(R.string.card_cancel)) } },
        )
    }
    HomeScreen(
        state, viewModel::select, viewModel::tap, { id, c -> viewModel.send(id, c, cardOf(id)) }, snackbar,
        onCustomize = viewModel::customize,
        onOpenSettings = onOpenSettings,
        onOpenConnectionSettings = onOpenConnectionSettings,
        route = route,
        onRouteChange = onRouteChange,
        windowInsets = windowInsets,
        onSearch = viewModel::search,
        onOpenHistory = onOpenHistory,
        onSendGroup = { id, c -> viewModel.sendGroup(id, c, group = cardOf(id)) },
        onSendRoom = viewModel::sendRoom,
        onCustomizeGroup = { id, name, favorite -> viewModel.customizeGroup(id, name, favorite, cardOf(id)) },
        voiceEnabled = voiceEnabled,
        dashboard = dashboard,
        onSelectDashboard = dashboardViewModel::select,
        onRunFlow = dashboardViewModel::runFlow,
        onOpenEnergy = onOpenEnergy,
        onStartBoost = viewModel::startBoost,
        onCancelBoost = viewModel::cancelBoost,
        onDismissClimatError = viewModel::dismissClimatError,
        climatResults = viewModel.climatResults,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelect: (RoomFilter) -> Unit,
    onTap: (CardUi) -> Unit,
    onSend: (String, Command) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    now: LocalTime = LocalTime.now(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    onCustomize: (String, List<Area>, UiOverride) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = onOpenSettings,
    route: Route? = null,
    onRouteChange: (Route) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onOpenHistory: (String) -> Unit = {},
    onSendGroup: (String, Command) -> Unit = { _, _ -> },
    onSendRoom: (areaId: String, kind: RoomKind) -> Unit = { _, _ -> },
    onCustomizeGroup: (String, String, Boolean) -> Unit = { _, _, _ -> },
    voiceEnabled: Boolean = false,
    dashboard: DashboardScreenState = DashboardScreenState(),
    onSelectDashboard: (String) -> Unit = {},
    onRunFlow: (String) -> Unit = {},
    onOpenEnergy: () -> Unit = {},
    onStartBoost: (zone: String, setpoint: Double, minutes: Int) -> Unit = { _, _, _ -> },
    onCancelBoost: (zone: String) -> Unit = {},
    onDismissClimatError: () -> Unit = {},
    climatResults: Flow<ClimatResult> = emptyFlow(),
) {
    val layoutDirection = LocalLayoutDirection.current
    // Rooms design §6.5: the search field is always there on Rooms, in a room and on All devices.
    // Elsewhere (Favourites, Hidden, a dashboard) the header keeps its magnifier, which opens it.
    val dashboardSelected = dashboard.switcherVisible && dashboard.view !is DashboardView.House
    val fieldAlways = !dashboardSelected &&
        (state.selected == RoomFilter.Rooms || state.selected == RoomFilter.All || state.selected is RoomFilter.Area)
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotBlank()) }
    // The screen owns the text: state.query comes back off Main, a frame or more late, and feeding it
    // back as the value would reset the field (and the IME) to the stale text on every keystroke.
    var searchText by rememberSaveable { mutableStateOf(state.query) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val searching = searchText.isNotBlank() || state.query.isNotBlank() || (searchOpen && !fieldAlways)
    val closeSearch = {
        focusManager.clearFocus()
        searchOpen = false
        searchText = ""
        onSearch("")
    }
    // Switching view ends a search (the view model clears the query with it): empty the field too.
    var lastSelected by rememberSaveable { mutableStateOf(state.selected.key) }
    LaunchedEffect(state.selected.key) {
        if (lastSelected != state.selected.key) {
            lastSelected = state.selected.key
            searchOpen = false
            searchText = ""
        }
    }
    // Back from a room goes to Rooms. Declared before the search handler: the last enabled one wins, so search closes first.
    val roomInView = (state.selected as? RoomFilter.Area)
        ?.takeIf { !dashboardSelected }
        ?.let { area -> state.roomCards.firstOrNull { it.areaId == area.id } }
    BackHandler(enabled = roomInView != null) { onSelect(RoomFilter.Rooms) }
    BackHandler(enabled = fieldFocused && !searching) { focusManager.clearFocus() }
    BackHandler(enabled = searching, onBack = closeSearch)
    var sheetId by rememberSaveable { mutableStateOf<String?>(null) }
    var customizeId by rememberSaveable { mutableStateOf<String?>(null) }
    var linkSheet by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var boostSheet by rememberSaveable { mutableStateOf(false) }
    // The boost sheet closes once the hub took what it sent; a refused command from the banner (sheet
    // closed) is a snackbar. One-shot results, collected under a stable key, so neither is lost.
    val climatError = stringResource(R.string.climat_error)
    val dismissClimatError by rememberUpdatedState(onDismissClimatError)
    LaunchedEffect(climatResults, snackbarHostState) {
        climatResults.collect { result ->
            when (result) {
                ClimatResult.CONFIRMED -> boostSheet = false
                ClimatResult.REFUSED -> if (!boostSheet) {
                    launch {
                        dismissClimatError()
                        snackbarHostState.showSnackbar(climatError)
                    }
                }
            }
        }
    }
    // Searching always looks through House, even from a dashboard.
    val onDashboard = dashboardSelected && !searching
    val shownDashboard = (dashboard.view as? DashboardView.Shown)?.ui
    val showDashboardHeader = !onDashboard || shownDashboard?.showHeader == true
    val cardClick: (CardUi) -> Unit = { card ->
        when (card.tap) {
            TapAction.OPEN_SHEET -> sheetId = card.id
            TapAction.OPEN_HISTORY -> onOpenHistory(card.id)
            else -> onTap(card)
        }
    }
    val cardLongClick: (CardUi) -> Unit = { card ->
        // Room groups spec §3: a light group's long-press opens its sheet (one switch per light);
        // a shutter group's opens Customize. Either sheet reaches the other.
        val customizes = if (card.isGroup) card.kind == Kind.COVER else !card.hasSheet
        if (customizes) customizeId = card.id else sheetId = card.id
    }
    val cardForce: (CardUi) -> Unit = { card ->
        val next = if (card.forceToggle == true) "eco" else "performance"
        onSend(card.id, Command.SetHvacMode(next))
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = NidooTheme.colors.content,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = windowInsets,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth < 600.dp -> 2
                maxWidth < 840.dp -> 3
                else -> 4
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.testTag("home-grid"),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(NidooDimens.gridGap),
                verticalArrangement = Arrangement.spacedBy(NidooDimens.gridGap),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) { HomeHeader(state, now, dashboard = dashboard, showGreeting = showDashboardHeader, room = roomInView.takeIf { !searching }, onSwitcherClick = { picker = true }, onPillClick = { linkSheet = true }, onHeroLongClick = { state.hero?.let { customizeId = it.id } }, showSearch = !fieldAlways, onSearchClick = { searchOpen = true }, onSettingsClick = onOpenSettings, voiceEnabled = voiceEnabled) }
                if (searching || fieldAlways) {
                    val opened = searchOpen && !fieldAlways
                    item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                        HomeSearchField(
                            text = searchText,
                            onTextChange = { searchText = it; onSearch(it) },
                            onClear = closeSearch,
                            showClear = searchText.isNotEmpty() || opened,
                            autoFocus = opened,
                            onFocusChange = { fieldFocused = it },
                        )
                    }
                }
                // Results replace the pills while searching.
                if (!searching && showDashboardHeader) {
                    item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                        // On a dashboard a pill goes back to House, on that view.
                        HomeChips(state, inRoom = roomInView != null, onSelect = { if (onDashboard) onSelectDashboard(HOUSE_DASHBOARD_ID); onSelect(it) })
                    }
                }
                val climat = state.climat
                if (climat != null && !onDashboard && !searching) {
                    item(key = "climat-banner", span = { GridItemSpan(maxLineSpan) }) {
                        ClimatBanner(climat, onBoost = { boostSheet = true }, onCancel = { onCancelBoost(climat.zone) })
                    }
                }
                if (onDashboard) {
                    when (val view = dashboard.view) {
                        is DashboardView.Loading -> item(key = "dashboard-loading", span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.dashboard_loading), style = NidooTheme.type.body, modifier = Modifier.padding(vertical = 24.dp))
                        }
                        is DashboardView.Shown -> if (view.ui.tiles.isEmpty()) {
                            item(key = "dashboard-empty", span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.dashboard_empty), style = NidooTheme.type.body, modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else {
                            dashboardTiles(
                                view.ui.tiles, dashboard.runningFlows,
                                onEntityClick = cardClick, onEntityLongClick = cardLongClick,
                                onSendGroup = onSendGroup, onForce = cardForce, onSend = onSend,
                                onRunFlow = onRunFlow, onOpenEnergy = onOpenEnergy, onOpenHistory = onOpenHistory,
                            )
                        }
                        DashboardView.House -> Unit
                    }
                }
                if (!onDashboard && !searching && state.selected == RoomFilter.Rooms) {
                    // Two columns per card where they pair up; on an odd grid (3 columns) a 2-span card would leave a column empty on every row.
                    items(state.roomCards, key = { "room-" + it.areaId }, span = { GridItemSpan(if (maxLineSpan % 2 == 0) 2 else maxLineSpan) }) { room ->
                        RoomCard(
                            room,
                            onOpen = { onSelect(RoomFilter.Area(room.areaId)) },
                            onLights = { onSendRoom(room.areaId, RoomKind.LIGHT) },
                            onShutters = { onSendRoom(room.areaId, RoomKind.COVER) },
                        )
                    }
                }
                if (searching && state.query.isNotBlank() && state.cards.isEmpty()) {
                    item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.search_no_match, state.query.trim()), style = NidooTheme.type.body, modifier = Modifier.padding(vertical = 24.dp))
                    }
                }
                if (state.loading && !onDashboard) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.home_discovering), style = NidooTheme.type.body, modifier = Modifier.padding(vertical = 24.dp))
                    }
                }
                if (!onDashboard) {
                    if (state.sections.isEmpty() && state.strip.isEmpty()) {
                        // Favourites, Hidden and search results: one flat grid.
                        homeCards(state.cards, cardClick, cardLongClick, cardForce, onSend, onSendGroup)
                    } else {
                        // A room or All devices (rooms design §6.4): sensors as pills, then one block per type.
                        if (state.strip.isNotEmpty()) {
                            item(key = "strip", span = { GridItemSpan(maxLineSpan) }) {
                                SensorStrip(state.strip, onClick = cardClick, onLongClick = cardLongClick)
                            }
                        }
                        state.sections.forEach { section ->
                            if (section.showLabel) {
                                item(key = "section-" + section.section.name, span = { GridItemSpan(maxLineSpan) }) { SectionLabel(section.section) }
                            }
                            homeCards(section.cards, cardClick, cardLongClick, cardForce, onSend, onSendGroup)
                        }
                    }
                }
            }
            // Edge-to-edge: keep scrolled cards from colliding with the clock and status icons.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(padding.calculateTopPadding())
                    .background(NidooTheme.colors.backgroundTop.copy(alpha = 0.92f))
                    .testTag("status-scrim"),
            )
        }
    }
    val climatUi = state.climat
    if (boostSheet && climatUi != null) {
        BoostSheet(
            climatUi,
            sending = state.climatSending,
            error = state.climatError,
            onStart = { target, minutes -> onStartBoost(climatUi.zone, target, minutes) },
            onCancel = { onCancelBoost(climatUi.zone) },
            onDismiss = { boostSheet = false; onDismissClimatError() },
        )
    }
    val sheetCard = sheetId?.let { state.card(it) ?: dashboard.card(it) }
    if (sheetCard != null) {
        // Keyed: a dismissed sheet's state stays hidden, so the group sheet a member returns to needs a fresh one.
        key(sheetCard.id) {
            ControlSheet(
                sheetCard,
                onSend = onSend,
                onCustomize = { sheetId = null; customizeId = sheetCard.id },
                onDismiss = { sheetId = null },
                onOpenHistory = { sheetId = null; onOpenHistory(sheetCard.id) },
                onSendGroup = onSendGroup,
                onCustomizeMember = { member -> sheetId = null; customizeId = member },
            )
        }
    }
    val customizeCard = customizeId?.let { state.card(it) ?: dashboard.card(it) }
    if (customizeCard != null) {
        if (customizeCard.isGroup) {
            GroupCustomizeSheet(
                customizeCard,
                onSave = { name, favorite -> onCustomizeGroup(customizeCard.id, name, favorite) },
                onDismiss = { customizeId = null },
            )
        } else {
            CustomizeSheet(
                customizeCard,
                state.rooms,
                onSave = { newRooms, override -> onCustomize(customizeCard.id, newRooms, override) },
                onDismiss = { customizeId = null },
            )
        }
    }
    if (picker) {
        DashboardPickerSheet(
            dashboard.entries, dashboard.selectedId,
            onPick = { picker = false; onSelectDashboard(it) },
            onDismiss = { picker = false },
        )
    }
    if (linkSheet) {
        LinkSheet(
            state.link,
            route = route,
            onRouteChange = onRouteChange,
            onOpenSettings = { linkSheet = false; onOpenConnectionSettings() },
            onDismiss = { linkSheet = false },
        )
    }
}

/** One block of cards: a tile per device, a wide card per group. */
private fun LazyGridScope.homeCards(
    cards: List<CardUi>,
    cardClick: (CardUi) -> Unit,
    cardLongClick: (CardUi) -> Unit,
    cardForce: (CardUi) -> Unit,
    onSend: (String, Command) -> Unit,
    onSendGroup: (String, Command) -> Unit,
) = items(cards, key = { it.id }, span = { GridItemSpan(if (it.wide) minOf(2, maxLineSpan) else 1) }) { card ->
    val onClick = { cardClick(card) }
    if (card.isGroup) {
        // Group: tap opens the sheet; long-press opens it too for lights, Customize for shutters; the inline buttons drive the group directly.
        GroupCard(
            card,
            onClick = onClick,
            onLongClick = { cardLongClick(card) },
            onSendGroup = onSendGroup,
        )
    } else {
        EntityCard(
            card = card,
            onClick = onClick,
            onLongClick = { cardLongClick(card) },
            onForce = { cardForce(card) },
            onSend = { onSend(card.id, it) },
        )
    }
}

@Composable
internal fun sectionLabel(section: HomeSection): String = stringResource(
    when (section) {
        HomeSection.LIGHTS -> R.string.section_lights
        HomeSection.SHUTTERS_CLIMATE -> R.string.section_shutters_climate
        HomeSection.PLUGS_MEDIA -> R.string.section_plugs_media
        HomeSection.OTHER -> R.string.section_other
        HomeSection.SENSORS -> R.string.section_sensors
    },
)

@Composable
private fun SectionLabel(section: HomeSection) {
    Text(
        sectionLabel(section).uppercase(),
        style = NidooTheme.type.label,
        color = NidooTheme.colors.contentMuted,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp).testTag("section-" + section.name).semantics { heading() },
    )
}

private const val VOICE_ACTIVITY = "com.gekkotron.nidoo.feature.voice.VoiceActivity"

@Composable
private fun HomeHeader(state: HomeUiState, now: LocalTime, dashboard: DashboardScreenState = DashboardScreenState(), showGreeting: Boolean = true, room: RoomCardUi? = null, showSearch: Boolean = true, onSwitcherClick: () -> Unit = {}, onPillClick: () -> Unit = {}, onHeroLongClick: () -> Unit = {}, onSearchClick: () -> Unit = {}, onSettingsClick: () -> Unit = {}, voiceEnabled: Boolean = false) {
    val haptics = LocalHapticFeedback.current
    val customizeLabel = stringResource(R.string.action_customize)
    val staleText = stringResource(R.string.card_stale)
    val searchLabel = stringResource(R.string.search_open)
    Column(Modifier.padding(top = 8.dp)) {
        if (dashboard.switcherVisible) {
            val house = stringResource(R.string.dashboard_house)
            val view = dashboard.view
            val title = when {
                view is DashboardView.Shown -> view.ui.name
                view is DashboardView.Loading -> view.name ?: dashboard.entries.firstOrNull { it.id == dashboard.selectedId }?.name ?: "…"
                else -> house
            }
            DashboardSwitcher(title, onSwitcherClick)
        }
        if (showGreeting && room != null) {
            // Inside a room the title is the room and its status takes the weather's place (rooms design §6.4).
            Text(roomName(room), style = NidooTheme.type.display, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("room-title").semantics { heading() })
            Text(roomStatus(room), style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted, modifier = Modifier.testTag("room-status"))
        } else if (showGreeting) Row(verticalAlignment = Alignment.Bottom) {
            Text(greeting(now), style = NidooTheme.type.display, modifier = Modifier.weight(1f))
            state.hero?.let { hero ->
                Text(
                    cardValueText(hero.value),
                    style = NidooTheme.type.display,
                    // Long-press only, so it must not expose a click action TalkBack would announce as
                    // "double-tap to activate" and then do nothing (spec §5.2); see NidooCard for the same pattern.
                    modifier = Modifier
                        .testTag("hero")
                        .alpha(if (hero.stale) 0.6f else 1f)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHeroLongClick()
                            })
                        }
                        .semantics {
                            onLongClick(label = customizeLabel) { onHeroLongClick(); true }
                            if (hero.stale) stateDescription = staleText
                        },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinkPill(state.link, onClick = onPillClick)
            Spacer(Modifier.weight(1f))
            if (voiceEnabled) {
                val ctx = LocalContext.current
                val voiceLabel = stringResource(R.string.home_voice_cd)
                // Addressed by class name so :feature:home does not depend on :feature:voice.
                IconButton(onClick = { ctx.startActivity(Intent().setClassName(ctx, VOICE_ACTIVITY)) }) {
                    StaticIcon(NidooGlyph.MIC, modifier = Modifier.size(24.dp).semantics { contentDescription = voiceLabel })
                }
            }
            if (showSearch) IconButton(onClick = onSearchClick) {
                StaticIcon(NidooGlyph.SEARCH, modifier = Modifier.size(24.dp).semantics { contentDescription = searchLabel })
            }
            val settingsLabel = stringResource(R.string.settings_open)
            IconButton(onClick = onSettingsClick) {
                StaticIcon(NidooGlyph.GEAR, modifier = Modifier.size(24.dp).semantics { contentDescription = settingsLabel })
            }
        }
    }
}

@Composable
private fun HomeChips(state: HomeUiState, inRoom: Boolean, onSelect: (RoomFilter) -> Unit) {
    val all = stringResource(R.string.chip_all)
    val favorites = stringResource(R.string.chip_favorites)
    val rooms = stringResource(R.string.chip_rooms)
    val unassigned = stringResource(R.string.area_unassigned)
    val hidden = stringResource(R.string.chip_hidden, state.hiddenCount)
    // Inside a room the row is "‹ Rooms" then every room (rooms design §6.4); elsewhere, and on a
    // dashboard whatever House was left on, the top-level views.
    val back = stringResource(R.string.chip_back_rooms)
    val items = (if (inRoom) state.roomChips else state.chips).map { chip ->
        val label = chip.label ?: when (chip.filter) {
            RoomFilter.All -> all
            RoomFilter.Favorites -> favorites
            RoomFilter.Rooms -> rooms
            RoomFilter.Hidden -> hidden
            is RoomFilter.Area -> unassigned
        }
        ChipItem(chip.filter.key, label)
    }
    val row = if (inRoom) listOf(ChipItem(RoomFilter.Rooms.key, back)) + items else items
    RoomChips(row, state.selected.key, onSelect = { onSelect(RoomFilter.fromKey(it)) })
}

@Composable
private fun greeting(now: LocalTime): String = stringResource(
    when (now.hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    },
)
