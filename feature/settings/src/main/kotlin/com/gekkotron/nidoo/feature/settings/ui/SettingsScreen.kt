package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.permissions.openNotificationSettings
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.presentation.SettingsViewModel
import com.gekkotron.nidoo.feature.settings.presentation.appVersion
import com.gekkotron.nidoo.transport.Route
import kotlinx.coroutines.delay
import java.time.Instant

/** The Settings list. The push status is re-read on every resume: notification permission may have changed away. */
@Composable
fun SettingsRoute(
    onOpenPage: (SettingsPage) -> Unit,
    onOpenRooms: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPush()
        onPauseOrDispose {}
    }
    SettingsListScreen(state, version, onOpenPage, onOpenRooms, onBack)
}

/** One Settings sub-page, wired to the view-model. */
@Composable
fun SettingsPageRoute(
    page: SettingsPage,
    route: Route?,
    onRouteChange: (Route) -> Unit,
    onSetUpAgain: () -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    clock: () -> Instant = Instant::now,
    presenceSection: @Composable (onInfo: (String) -> Unit) -> Unit = { info -> PresenceSettingsSlot(onInfoMessage = info, showTitle = false) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    // The "N min ago" text must move on without a new status emission, so the clock is state here. Only the
    // Notifications page shows the push status, so only it ticks and re-reads it (settings sub-pages spec).
    var now by remember { mutableStateOf(clock()) }
    if (page == SettingsPage.NOTIFICATIONS) {
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(60_000)
                    now = clock()
                }
            }
        }
        LifecycleResumeEffect(viewModel) {
            now = clock()
            viewModel.refreshPush()
            onPauseOrDispose {}
        }
    }
    SettingsPageScreen(
        page, state, version, route, now, onBack,
        onTheme = viewModel::setTheme, onEnergyView = viewModel::setEnergyView,
        onRegisterAgain = viewModel::registerAgain, onOpenChannels = { openNotificationSettings(context) },
        onRouteChange = onRouteChange, onSetUpAgain = onSetUpAgain,
        onSaveHistoryUrl = viewModel::setHistoryUrl, onSaveFrigate = viewModel::setFrigate,
        onVoiceEnabled = viewModel::setVoiceEnabled,
        onOpenLicences = onOpenLicences, presenceSection = presenceSection,
    )
}

/** Shared by the Settings screens: a back arrow and a title on the transparent background. No title on the list, whose header carries it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(title: String?, onBack: () -> Unit) {
    val backLabel = stringResource(R.string.settings_back)
    TopAppBar(
        title = { if (title != null) Text(title, style = NidooTheme.type.display) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) {
                Text("←", style = NidooTheme.type.cardValue, modifier = Modifier.clearAndSetSemantics {})
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = NidooTheme.colors.content, navigationIconContentColor = NidooTheme.colors.content),
    )
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun NavRow(title: String, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = NidooDimens.minTouch).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NidooTheme.type.body)
            if (summary != null) Text(summary, style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted)
        }
        Text("›", style = NidooTheme.type.cardValue, color = NidooTheme.colors.contentMuted, modifier = Modifier.clearAndSetSemantics {})
    }
}
