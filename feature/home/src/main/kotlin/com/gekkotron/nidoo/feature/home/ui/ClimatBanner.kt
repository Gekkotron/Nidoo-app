package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardFormatter
import com.gekkotron.nidoo.feature.home.presentation.ClimatBannerUi
import com.gekkotron.nidoo.feature.home.presentation.ClimatWhy
import com.gekkotron.nidoo.feature.home.presentation.StepMath
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The boost lengths the app offers, in minutes (spec §6). */
internal val BOOST_DURATIONS = listOf(60, 90, 120)
private const val BOOST_STEP = 0.5
private const val DEFAULT_BOOST_TARGET = 20.0

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Unix seconds as the phone's local "HH:mm". */
private fun clock(unixSec: Long): String = clockFormat.format(Instant.ofEpochSecond(unixSec).atZone(ZoneId.systemDefault()))

private fun degrees(value: Double?, locale: Locale): String = (value?.let { CardFormatter.number(it, locale) } ?: "—") + "°"

@Composable
private fun whyText(why: ClimatWhy): String = stringResource(
    when (why) {
        ClimatWhy.SCHEDULE -> R.string.climat_why_schedule
        ClimatWhy.BOOST -> R.string.climat_why_boost
        ClimatWhy.HOLD -> R.string.climat_why_hold
        ClimatWhy.ECO -> R.string.climat_why_eco
        ClimatWhy.AWAY -> R.string.climat_why_away
        ClimatWhy.HOLIDAY -> R.string.climat_why_holiday
        ClimatWhy.EMPTY -> R.string.climat_why_empty
        ClimatWhy.SAFETY -> R.string.climat_why_safety
        ClimatWhy.IDLE -> R.string.climat_why_idle
        ClimatWhy.OTHER -> R.string.climat_why_other
    },
)

/** The room's Climat banner (mockup option A): room reading, Climat's target and why, with Boost or Cancel. */
@Composable
internal fun ClimatBanner(ui: ClimatBannerUi, onBoost: () -> Unit, onCancel: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    NidooCard(
        modifier = Modifier.fillMaxWidth().testTag("climat-banner"),
        accent = ui.boost != null,
        onClick = if (ui.canBoost) onBoost else null,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.climat_title), style = NidooTheme.type.body)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(degrees(ui.reading, locale), style = NidooTheme.type.display.copy(fontSize = 36.sp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.sheet_target), style = NidooTheme.type.body)
                    Text(degrees(ui.target, locale), style = NidooTheme.type.display.copy(fontSize = 22.sp))
                }
            }
            val status = when {
                ui.boost != null -> stringResource(R.string.climat_boost_until, CardFormatter.number(ui.boost.target, locale), clock(ui.boost.until))
                ui.holdUntil != null -> stringResource(R.string.climat_hold_until, clock(ui.holdUntil))
                // Safety says why it acts (spec §6.3); the schedule says which block it follows.
                ui.why in setOf(ClimatWhy.SCHEDULE, ClimatWhy.SAFETY) && ui.reason.isNotBlank() -> whyText(ui.why) + " · " + ui.reason
                else -> whyText(ui.why)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = NidooTheme.type.body, modifier = Modifier.weight(1f))
                when {
                    ui.boost != null || ui.holdUntil != null -> TextButton(onClick = onCancel) { Text(stringResource(R.string.climat_cancel)) }
                    ui.canBoost -> FilledTonalButton(onClick = onBoost) { Text(stringResource(R.string.climat_boost)) }
                }
            }
        }
    }
}

/** The duration closest to [remainingMin], so reopening a running boost shows its own length. */
private fun nearestDuration(remainingMin: Long): Int = BOOST_DURATIONS.minBy { kotlin.math.abs(it - remainingMin) }

/**
 * The boost sheet: a room target (0.5° steps within the zone's devices' range) and a length, then
 * Start boost, or Update and Cancel while one runs. Buttons wait while [sending].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BoostSheet(
    ui: ClimatBannerUi,
    sending: Boolean,
    error: Boolean,
    onStart: (Double, Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val nowSec = remember { System.currentTimeMillis() / 1_000 }
    val start = (ui.boost?.target ?: ui.target ?: DEFAULT_BOOST_TARGET).coerceIn(ui.min, ui.max)
    var target by remember(ui.zone) { mutableDoubleStateOf(start) }
    var minutes by remember(ui.zone) { mutableIntStateOf(ui.boost?.let { nearestDuration((it.until - nowSec) / 60) } ?: BOOST_DURATIONS.first()) }
    val decrease = stringResource(R.string.sheet_decrease)
    val increase = stringResource(R.string.sheet_increase)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.climat_boost), style = NidooTheme.type.display.copy(fontSize = 22.sp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledTonalIconButton(
                    onClick = { target = StepMath.stepTemperature(target, -BOOST_STEP, ui.min, ui.max) },
                    enabled = target > ui.min,
                    modifier = Modifier.semantics { contentDescription = decrease },
                ) { Text("−", fontSize = 22.sp) }
                Text(degrees(target, locale), style = NidooTheme.type.display)
                FilledTonalIconButton(
                    onClick = { target = StepMath.stepTemperature(target, BOOST_STEP, ui.min, ui.max) },
                    enabled = target < ui.max,
                    modifier = Modifier.semantics { contentDescription = increase },
                ) { Text("+", fontSize = 22.sp) }
            }
            val presets = listOf(start - 1, start + 1, start + 2).map { it.coerceIn(ui.min, ui.max) }.distinct().filter { it != start }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (preset in presets) {
                    FilterChip(selected = target == preset, onClick = { target = preset }, label = { Text(degrees(preset, locale)) })
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (d in BOOST_DURATIONS) {
                    val label = when (d) {
                        60 -> R.string.climat_duration_60
                        90 -> R.string.climat_duration_90
                        else -> R.string.climat_duration_120
                    }
                    FilterChip(selected = minutes == d, onClick = { minutes = d }, label = { Text(stringResource(label)) })
                }
            }
            Text(
                stringResource(R.string.climat_sheet_line, CardFormatter.number(target, locale), clock(nowSec + minutes * 60L)),
                style = NidooTheme.type.body,
            )
            if (error) Text(stringResource(R.string.climat_error), style = NidooTheme.type.body, color = NidooTheme.colors.warning)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.boost != null) {
                    OutlinedButton(onClick = onCancel, enabled = !sending, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.climat_cancel))
                    }
                }
                Button(onClick = { onStart(target, minutes) }, enabled = !sending, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (ui.boost != null) R.string.climat_update else R.string.climat_start))
                }
            }
        }
    }
}
