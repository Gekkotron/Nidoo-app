package com.gekkotron.oikos.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gekkotron.oikos.designsystem.OikosTheme
import com.gekkotron.oikos.designsystem.icons.OikosGlyph
import com.gekkotron.oikos.designsystem.icons.StaticIcon
import com.gekkotron.oikos.feature.home.R
import com.gekkotron.oikos.feature.home.presentation.SwitcherEntry

/** First line of Home's header: the dashboard in view, and a tap to change it. */
@Composable
internal fun DashboardSwitcher(title: String, onClick: () -> Unit) {
    val description = stringResource(R.string.dashboard_switcher_cd, title)
    val action = stringResource(R.string.dashboard_switcher_action)
    Text(
        "$title ▾",
        style = OikosTheme.type.cardName,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 12.dp),
    )
}

/** House first (the app's own Home), then the hub's dashboards in the order the hub gives them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardPickerSheet(entries: List<SwitcherEntry>, selectedId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.dashboard_picker_title), style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted, modifier = Modifier.padding(bottom = 8.dp))
            entries.forEach { entry ->
                val selected = entry.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(entry.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(entry.name ?: stringResource(R.string.dashboard_house), style = OikosTheme.type.cardName, modifier = Modifier.weight(1f))
                    if (selected) StaticIcon(OikosGlyph.DOT, modifier = Modifier.size(12.dp).clearAndSetSemantics {})
                }
            }
        }
    }
}
