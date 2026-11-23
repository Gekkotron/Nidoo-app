package com.gekkotron.nidoo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.designsystem.components.LinkPill
import com.gekkotron.nidoo.feature.onboarding.ui.OnboardingRoute
import com.gekkotron.nidoo.shared.resources.Res
import com.gekkotron.nidoo.shared.resources.house_devices
import com.gekkotron.nidoo.shared.resources.house_set_up_again
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The iOS app (spec §7.6 phase 4): onboarding until a profile is saved, then the house. Home replaces the summary in 4B. */
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
                        // No push on iOS, and no Rooms step before 4B: both steps are closed without being shown.
                        if (p.notificationsPending) LaunchedEffect(Unit) { shell.finishNotifications() }
                        if (p.roomsPending) LaunchedEffect(Unit) { shell.finishRooms() }
                        // A new profile gets a new ViewModel, so a new house never shows the previous one's source.
                        ScreenViewModels(p.profile) { HouseSummary(onSetUpAgain = shell::setUpAgain) }
                    }
                }
            }
        }
    }
}

/**
 * Gives [content] its own ViewModel store, cleared when it leaves the composition or [key] changes. Without it every
 * `koinViewModel()` resolves from the root store and gets back the instance of the previous visit (Android's nav entries
 * each have a store; 4C's Navigation 3 shell brings the same on iOS).
 */
@Composable
private fun ScreenViewModels(key: Any?, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

@Composable
private fun HouseSummary(onSetUpAgain: () -> Unit, viewModel: HouseSummaryViewModel = koinViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nidoo", style = NidooTheme.type.display, color = NidooTheme.colors.content)
        LinkPill(summary.link, onClick = null)
        Text(
            pluralStringResource(Res.plurals.house_devices, summary.devices, summary.devices),
            style = NidooTheme.type.body,
            color = NidooTheme.colors.contentMuted,
        )
        OutlinedButton(onClick = onSetUpAgain) { Text(stringResource(Res.string.house_set_up_again)) }
    }
}
