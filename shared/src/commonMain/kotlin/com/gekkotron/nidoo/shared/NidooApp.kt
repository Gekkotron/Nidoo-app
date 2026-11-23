package com.gekkotron.nidoo.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.rooms.RoomsRoute
import com.gekkotron.nidoo.feature.home.ui.HomeRoute
import com.gekkotron.nidoo.feature.onboarding.ui.OnboardingRoute
import org.koin.compose.viewmodel.koinViewModel

/** The iOS app (spec §7.6 phase 4): onboarding until a profile is saved, then the Rooms step for a new setup, then Home. */
@Composable
fun NidooApp(shell: AppShellViewModel = koinViewModel()) {
    val theme by shell.theme.collectAsStateWithLifecycle()
    val profile by shell.profile.collectAsStateWithLifecycle()
    NidooTheme(theme ?: ThemeChoice.SYSTEM) {
        // Until the preferences have loaded only the background is drawn, so nothing flashes in the wrong theme.
        NidooBackground(Modifier.fillMaxSize()) {
            if (theme != null) {
                when (val p = profile) {
                    ProfileState.Loading -> Unit
                    // Each visit gets fresh ViewModels: the root store (the iOS view controller's) lives for the whole app.
                    ProfileState.None, ProfileState.Locked -> ScreenViewModels(Unit) { OnboardingRoute() }
                    is ProfileState.Ready -> {
                        // No push on iOS (spec §3): the notification step is closed without being shown.
                        if (p.notificationsPending) LaunchedEffect(Unit) { shell.finishNotifications() }
                        if (p.roomsPending) {
                            ScreenViewModels(p.profile to "rooms") { RoomsRoute(onDone = shell::finishRooms) }
                        } else {
                            // A new profile gets a new ViewModel, so a new house never shows the previous one's source.
                            ScreenViewModels(p.profile) { HomeWithSettings(shell) }
                        }
                    }
                }
            }
        }
    }
}

/** Home, with [InterimSettings] in its place while open (4C's Settings screens replace it). */
@OptIn(ExperimentalComposeUiApi::class) // the common BackHandler (iOS edge swipe)
@Composable
private fun HomeWithSettings(shell: AppShellViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val route by shell.route.collectAsStateWithLifecycle()
    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        InterimSettings(onSetUpAgain = shell::setUpAgain, onBack = { settingsOpen = false })
    } else {
        // Energy, History and Security are Phase 5: no history or voice callback, so Home hides those actions.
        HomeRoute(
            onOpenSettings = { settingsOpen = true },
            onOpenConnectionSettings = { settingsOpen = true },
            route = route,
            onRouteChange = shell::setRoute,
        )
    }
}

/**
 * Gives [content] its own ViewModel store, cleared when it leaves the composition or [key] changes. Without it every
 * `koinViewModel()` resolves from the root store and gets back the instance of the previous visit (Android's nav entries
 * each have a store; 4C's Navigation 3 shell brings the same on iOS).
 */
@Composable
private fun ScreenViewModels(key: Any?, content: @Composable () -> Unit) {
    val owner = remember(key) { ScreenOwner() }
    DisposableEffect(owner) { onDispose { owner.destroy() } }
    // Only the ViewModel store is provided: Home keeps the root lifecycle for collectAsStateWithLifecycle.
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/**
 * A screen's ViewModel store with its own saved state: Home's ViewModels take a `SavedStateHandle`, which Koin builds from
 * this owner's creation extras. It enables saved-state handles itself because iOS's root owner never does. iOS has no
 * process-death restore, so the state is kept in memory only.
 */
internal class ScreenOwner : ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    private val registry = LifecycleRegistry.createUnsafe(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = throw UnsupportedOperationException("ViewModels come from Koin's factory")
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@ScreenOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@ScreenOwner
        }

    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        viewModelStore.clear()
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
