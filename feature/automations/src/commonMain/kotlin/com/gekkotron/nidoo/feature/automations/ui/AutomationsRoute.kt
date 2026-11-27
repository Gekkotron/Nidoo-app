package com.gekkotron.nidoo.feature.automations.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gekkotron.nidoo.feature.automations.presentation.AutomationsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AutomationsRoute(
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    viewModel: AutomationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationsScreen(
        state,
        onChipSelected = viewModel::onChipSelected,
        onToggle = viewModel::onToggle,
        onToastShown = viewModel::onToastShown,
        onSectionSelected = viewModel::onSectionSelected,
        onAlarmTapped = viewModel::onAlarmTapped,
        onEditorTime = viewModel::onEditorTime,
        onEditorDayToggled = viewModel::onEditorDayToggled,
        onEditorDismissed = viewModel::onEditorDismissed,
        onEditorSave = viewModel::onEditorSave,
        windowInsets = windowInsets,
    )
}
