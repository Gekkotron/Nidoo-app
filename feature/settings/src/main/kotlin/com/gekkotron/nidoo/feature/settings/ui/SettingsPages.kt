package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.data.DEFAULT_FRIGATE_URL
import com.gekkotron.nidoo.data.EnergyViewPreference
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooRadioRow
import com.gekkotron.nidoo.designsystem.components.RoutePicker
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.feature.settings.presentation.ConnectionSummary
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.presentation.SettingsUi
import com.gekkotron.nidoo.transport.Route

/** The page's top-bar title: the same words as its row in the Settings list. */
private fun SettingsPage.title(): Int = when (this) {
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.NOTIFICATIONS -> R.string.settings_notifications
    SettingsPage.PRESENCE -> R.string.presence_section_title
    SettingsPage.VOICE -> R.string.voice_section_title
    SettingsPage.CONNECTION -> R.string.settings_connection
    SettingsPage.ABOUT -> R.string.settings_about
}

/** One Settings sub-page (settings sub-pages spec): today's section content, moved as-is under its own top bar. */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    state: SettingsUi,
    version: String,
    route: Route?,
    onBack: () -> Unit,
    onTheme: (ThemePreference) -> Unit = {},
    onEnergyView: (EnergyViewPreference) -> Unit = {},
    onRouteChange: (Route) -> Unit = {},
    onSetUpAgain: () -> Unit = {},
    onSaveHistoryUrl: (String) -> Boolean = { true },
    onSaveFrigate: (url: String, user: String, password: String) -> Boolean = { _, _, _ -> true },
    onOpenLicences: (() -> Unit)? = null,
    platformPage: @Composable (SettingsPage, SnackbarHostState) -> Unit = { _, _ -> },
) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = NidooTheme.colors.content,
        topBar = { SettingsTopBar(stringResource(page.title()), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (page) {
                SettingsPage.APPEARANCE -> AppearanceBody(state, onTheme, onEnergyView)
                SettingsPage.NOTIFICATIONS, SettingsPage.PRESENCE, SettingsPage.VOICE -> platformPage(page, snackbar)
                SettingsPage.CONNECTION -> ConnectionBody(state, route, onRouteChange, onSetUpAgain, onSaveHistoryUrl, onSaveFrigate)
                SettingsPage.ABOUT -> AboutBody(version, onOpenLicences)
            }
        }
    }
}

@Composable
private fun AppearanceBody(state: SettingsUi, onTheme: (ThemePreference) -> Unit, onEnergyView: (EnergyViewPreference) -> Unit) {
    Section(stringResource(R.string.settings_theme)) {
        Column(Modifier.selectableGroup()) {
            NidooRadioRow(stringResource(R.string.settings_theme_system), state.theme == ThemePreference.SYSTEM, { onTheme(ThemePreference.SYSTEM) })
            NidooRadioRow(stringResource(R.string.settings_theme_night), state.theme == ThemePreference.NIGHT_GLASS, { onTheme(ThemePreference.NIGHT_GLASS) })
            NidooRadioRow(stringResource(R.string.settings_theme_ivoire), state.theme == ThemePreference.IVOIRE, { onTheme(ThemePreference.IVOIRE) })
        }
    }
    Section(stringResource(R.string.settings_energy_view)) {
        EnergyViewSection(state.energyView, onEnergyView)
    }
}

@Composable
private fun ConnectionBody(
    state: SettingsUi,
    route: Route?,
    onRouteChange: (Route) -> Unit,
    onSetUpAgain: () -> Unit,
    onSaveHistoryUrl: (String) -> Boolean,
    onSaveFrigate: (url: String, user: String, password: String) -> Boolean,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var historyDialog by rememberSaveable { mutableStateOf(false) }
    var frigateDialog by rememberSaveable { mutableStateOf(false) }
    Column {
        ConnectionRows(state.connection, onEditHistory = { historyDialog = true }, onEditFrigate = { frigateDialog = true })
        if (route != null) RoutePicker(route, onRouteChange, Modifier.padding(top = 8.dp))
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_set_up_again))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_set_up_again_title)) },
            text = { Text(stringResource(R.string.settings_set_up_again_body)) },
            confirmButton = { TextButton(onClick = { confirm = false; onSetUpAgain() }) { Text(stringResource(R.string.settings_set_up_again_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    if (historyDialog) {
        val direct = state.connection as? ConnectionSummary.Direct
        HistoryUrlDialog(direct?.historyUrl.orEmpty(), direct?.historyDefault.orEmpty(), onSave = onSaveHistoryUrl, onDismiss = { historyDialog = false })
    }
    if (frigateDialog) {
        val direct = state.connection as? ConnectionSummary.Direct
        FrigateDialog(
            initialUrl = direct?.frigateUrl ?: DEFAULT_FRIGATE_URL,
            initialUser = direct?.frigateUser.orEmpty(),
            hasPassword = direct?.frigateHasPassword == true,
            onSave = onSaveFrigate,
            onDismiss = { frigateDialog = false },
        )
    }
}

@Composable
private fun AboutBody(version: String, onOpenLicences: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_version, version), style = NidooTheme.type.body)
        Text(stringResource(R.string.settings_notice), style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted)
        if (onOpenLicences != null) NavRow(stringResource(R.string.settings_licences), null, onOpenLicences)
    }
}
