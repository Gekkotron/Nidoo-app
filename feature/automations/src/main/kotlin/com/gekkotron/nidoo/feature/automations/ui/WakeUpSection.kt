package com.gekkotron.nidoo.feature.automations.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.feature.automations.R
import com.gekkotron.nidoo.feature.automations.presentation.AlarmEditor
import com.gekkotron.nidoo.feature.automations.presentation.AlarmRow
import com.gekkotron.nidoo.feature.automations.presentation.AutomationsUiState
import com.gekkotron.nidoo.feature.automations.presentation.weekdayText
import com.gekkotron.nidoo.flows.WakeUpTime
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val DAYS_IN_WEEK = 7

@Composable
internal fun WakeUpSection(
    state: AutomationsUiState,
    onToggle: (String, Boolean) -> Unit,
    onAlarmTapped: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.alarms.isEmpty() -> LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(NidooDimens.gridGap),
        ) { items(PLACEHOLDER_ROWS) { PlaceholderRow() } }
        // Offline with nothing cached means "not heard from the hub yet", not "no alarms".
        state.alarms.isEmpty() -> if (!state.isOffline) EmptyCopy(R.string.wake_up_empty_title, R.string.wake_up_empty_body, modifier)
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(NidooDimens.gridGap),
        ) {
            items(state.alarms, key = { it.id }) { row -> AlarmTile(row, state.isOffline, onToggle, onAlarmTapped) }
        }
    }
}

@Composable
private fun AlarmTile(row: AlarmRow, offline: Boolean, onToggle: (String, Boolean) -> Unit, onTapped: (String) -> Unit) {
    val locale = currentLocale()
    val summary = row.time?.let { alarmSummary(it, locale) } ?: stringResource(R.string.wake_up_unreadable)
    NidooCard(Modifier.fillMaxWidth(), minHeight = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .testTag("alarm_row_${row.id}")
                    .clickable(enabled = row.time != null && !offline) { onTapped(row.id) },
            ) {
                Text(row.name, style = NidooTheme.type.cardName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(summary, style = NidooTheme.type.label, color = NidooTheme.colors.contentMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = row.enabled,
                onCheckedChange = { onToggle(row.id, it) },
                enabled = row.toggleEnabled,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .testTag("alarm_switch_${row.id}")
                    .semantics { contentDescription = row.name },
            )
        }
    }
}

@Composable
private fun alarmSummary(time: WakeUpTime, locale: Locale): String {
    val clock = "%02d:%02d".format(time.hour, time.minute)
    val days = if (time.days.size == DAYS_IN_WEEK) {
        stringResource(R.string.wake_up_every_day)
    } else {
        weekdayText(time.days, WeekFields.of(locale).firstDayOfWeek) { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    return "$clock · $days"
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** 0 = Sunday .. 6 = Saturday, the hub's numbering. */
private fun dayIndex(d: DayOfWeek) = d.value % DAYS_IN_WEEK

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditorSheet(
    editor: AlarmEditor,
    onTime: (Int, Int) -> Unit,
    onDayToggled: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val locale = currentLocale()
    val picker = rememberTimePickerState(
        initialHour = editor.hour,
        initialMinute = editor.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    LaunchedEffect(picker.hour, picker.minute) { onTime(picker.hour, picker.minute) }
    val first = WeekFields.of(locale).firstDayOfWeek
    val days = (0 until DAYS_IN_WEEK).map { first.plus(it.toLong()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .testTag("alarm_editor")
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(editor.name, style = NidooTheme.type.cardName, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            TimePicker(picker)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { d ->
                    val idx = dayIndex(d)
                    FilterChip(
                        selected = idx in editor.days,
                        onClick = { onDayToggled(idx) },
                        enabled = !editor.saving,
                        label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
                        modifier = Modifier.testTag("alarm_day_$idx"),
                    )
                }
            }
            if (editor.days.isEmpty()) {
                Text(stringResource(R.string.wake_up_pick_a_day), style = NidooTheme.type.label, color = MaterialTheme.colorScheme.error)
            }
            editor.error?.let {
                Text(stringResource(it), style = NidooTheme.type.label, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSave, enabled = editor.canSave, modifier = Modifier.fillMaxWidth().testTag("alarm_save")) {
                if (editor.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.wake_up_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
