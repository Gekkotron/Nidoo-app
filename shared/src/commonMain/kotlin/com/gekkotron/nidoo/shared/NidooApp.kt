package com.gekkotron.nidoo.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.automations.ui.AutomationsRoute
import com.gekkotron.nidoo.feature.energy.ui.EnergyRoute
import com.gekkotron.nidoo.feature.history.HistoryTarget
import com.gekkotron.nidoo.feature.history.ui.HistoryRoute
import com.gekkotron.nidoo.feature.home.rooms.RoomsRoute
import com.gekkotron.nidoo.feature.home.ui.CustomizeEntitySheet
import com.gekkotron.nidoo.feature.home.ui.HomeRoute
import com.gekkotron.nidoo.feature.security.ui.SecurityRoute
import com.gekkotron.nidoo.feature.onboarding.ui.OnboardingRoute
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.rooms.RoomOrderRoute
import com.gekkotron.nidoo.feature.settings.ui.SettingsPageRoute
import com.gekkotron.nidoo.feature.settings.ui.SettingsRoute
import com.gekkotron.nidoo.feature.settings.ui.VoiceSettingsPage
import com.gekkotron.nidoo.feature.voice.VoicePermissions
import com.gekkotron.nidoo.feature.voice.ui.VoiceRoute
import com.gekkotron.nidoo.shared.tabs.MainTab
import com.gekkotron.nidoo.shared.tabs.MainTabBar
import com.gekkotron.nidoo.shared.tabs.MainTabsViewModel
import com.gekkotron.nidoo.shared.tabs.mainTabsFor
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The iOS app (spec §7.6 phase 4): onboarding until a profile is saved, then the Rooms step for a new setup, then Home and Settings. */
@Composable
fun NidooApp(shell: AppShellViewModel = koinViewModel()) {
    val theme by shell.theme.collectAsStateWithLifecycle()
    val profile by shell.profile.collectAsStateWithLifecycle()
    NidooTheme(theme ?: ThemeChoice.SYSTEM) {
        // Until the preferences have loaded only the background is drawn, so nothing flashes in the wrong theme.
        NidooBackground(Modifier.fillMaxSize()) {
            // Loading and a null theme occur only before the first load (stateIn seeds), so once the screens show, they and the back stack never leave composition.
            val p = profile
            val root = rootFor(p)
            if (theme != null && root != null) {
                // No push on iOS (spec §3): the notification step is closed without being shown.
                if ((p as? ProfileState.Ready)?.notificationsPending == true) LaunchedEffect(Unit) { shell.finishNotifications() }
                Screens(shell, root, p)
            }
        }
    }
}

/**
 * One back stack for the app's lifetime. A new root (onboarding finished, or "set up again") replaces the entries inside
 * it, so NavDisplay pops the old ones and clears their ViewModel stores. iOS has no process-death restore, so a plain
 * state list is enough.
 */
@Composable
private fun Screens(shell: AppShellViewModel, root: NavKey, profile: ProfileState) {
    val route by shell.route.collectAsStateWithLifecycle()
    val backStack = remember { mutableStateListOf(root) }
    LaunchedEffect(root) { resetToRoot(backStack, root) }
    var voiceOpen by remember { mutableStateOf(false) }
    val top = backStack.lastOrNull()
    val tabs = mainTabsFor(profile)
    // The demo has no hub: leave Routines if the demo is switched on while it is open.
    val showAutomations = MainTab.AUTOMATIONS in tabs
    LaunchedEffect(showAutomations, top) { if (!showAutomations && top == AutomationsKey) selectTab(backStack, MainTab.HOME) }
    val openConnectionSettings = {
        push(backStack, SettingsKey)
        push(backStack, SettingsPageKey(SettingsPage.CONNECTION))
    }
    // The bar owns the bottom inset on the main tabs; their own Scaffolds then only pad top and sides.
    val tabInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showsTabBar(top)) {
                val tabsVm = koinViewModel<MainTabsViewModel>()
                val status by tabsVm.status.collectAsStateWithLifecycle()
                MainTabBar(tabs, top?.mainTab(), { selectTab(backStack, it) }, status)
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<OnboardingKey> { OnboardingRoute() }
                entry<RoomsKey> {
                    RoomsRoute(onDone = {
                        // First run: finishing flips the root to Home, which replaces this entry. From Settings: pop back.
                        shell.finishRooms()
                        pop(backStack, RoomsKey)
                    })
                }
                entry<HomeKey> {
                    // Voice opens the overlay below; the mic shows only while Voice is on.
                    HomeRoute(
                        onOpenSettings = { push(backStack, SettingsKey) },
                        onOpenConnectionSettings = openConnectionSettings,
                        route = route,
                        onRouteChange = shell::setRoute,
                        windowInsets = tabInsets,
                        onOpenHistory = { push(backStack, HistoryKey(HistoryTarget.Entity(it).encode())) },
                        onOpenEnergy = { selectTab(backStack, MainTab.ENERGY) },
                        onOpenVoice = { voiceOpen = true },
                    )
                }
                entry<EnergyKey> {
                    EnergyRoute(
                        onOpenHome = { selectTab(backStack, MainTab.HOME) },
                        windowInsets = tabInsets,
                        onOpenHistory = { id -> push(backStack, HistoryKey((id?.let(HistoryTarget::Entity) ?: HistoryTarget.House).encode())) },
                        onOpenConnectionSettings = openConnectionSettings,
                        route = route,
                        onRouteChange = shell::setRoute,
                    )
                }
                entry<SecurityKey> {
                    // No recent-alerts section on iOS yet (Phase 6): alertsSection and alertsEffects stay null.
                    SecurityRoute(
                        windowInsets = tabInsets,
                        customizeSheet = { id, onDismiss, onSaveFailed -> CustomizeEntitySheet(id, onDismiss, onSaveFailed) },
                        onOpenHistory = { push(backStack, HistoryKey(HistoryTarget.Entity(it).encode())) },
                        onOpenConnectionSettings = openConnectionSettings,
                        route = route,
                        onRouteChange = shell::setRoute,
                    )
                }
                entry<AutomationsKey> { AutomationsRoute(windowInsets = tabInsets) }
                entry<HistoryKey> { key ->
                    val target = HistoryTarget.decode(key.target)
                    if (target == null) LaunchedEffect(key) { pop(backStack, key) }
                    else HistoryRoute(target, onBack = { pop(backStack, key) }, windowInsets = ScaffoldDefaults.contentWindowInsets)
                }
                entry<SettingsKey> {
                    SettingsRoute(
                        onOpenPage = { push(backStack, SettingsPageKey(it)) },
                        onOpenRooms = { push(backStack, RoomOrderKey) },
                        onBack = { pop(backStack, SettingsKey) },
                    )
                }
                entry<SettingsPageKey> { key ->
                    SettingsPageRoute(
                        page = key.page,
                        route = route,
                        onRouteChange = shell::setRoute,
                        onSetUpAgain = shell::setUpAgain,
                        onOpenLicences = null, // no licence report is bundled on iOS yet
                        platformPage = { page, snackbar -> if (page == SettingsPage.VOICE) VoiceSettingsPage(snackbar) },
                        onBack = { pop(backStack, key) },
                    )
                }
                entry<RoomOrderKey> {
                    RoomOrderRoute(onAssignRooms = { push(backStack, RoomsKey) }, onBack = { pop(backStack, RoomOrderKey) })
                }
            },
        )
    }
    if (voiceOpen) {
        val close = { voiceOpen = false }
        VoiceOverlay(
            onClose = close,
            onOpenSettings = {
                close()
                push(backStack, SettingsKey)
                push(backStack, SettingsPageKey(SettingsPage.VOICE))
            },
        )
    }
    }
}

/**
 * The voice sheet over the current screen. It owns its ViewModel store, so every opening starts a new session, and
 * clears the store when it leaves (VoiceRoute ends the session on dispose, which also frees the recognizer's handler).
 */
@Composable
private fun VoiceOverlay(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val store = remember { ViewModelStore() }
    DisposableEffect(store) {
        onDispose {
            store.clear() // VoiceRoute's cancel and onCleared have run: the audio session is idle and can be released
            onVoiceClosed()
        }
    }
    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        VoiceRoute(onClose = onClose, onOpenSettings = onOpenSettings, permissions = koinInject<VoicePermissions>())
    }
}
