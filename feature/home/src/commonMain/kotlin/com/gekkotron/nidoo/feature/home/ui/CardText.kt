package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.gekkotron.nidoo.designsystem.alarm.alarmLabel
import com.gekkotron.nidoo.feature.home.resources.*
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.CardValue
import com.gekkotron.nidoo.feature.home.presentation.HeaterChip
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Kind
import org.jetbrains.compose.resources.StringResource

private val OPENING_CLASSES = setOf("door", "window", "garage_door", "opening")
private val MOTION_CLASSES = setOf("motion", "occupancy", "presence")

@Composable
internal fun cardValueText(value: CardValue, kind: Kind = Kind.SENSOR): String = when (value) {
    CardValue.None -> stringResource(Res.string.value_none)
    is CardValue.OnOff -> stringResource(if (value.on) Res.string.value_on else Res.string.value_off)
    is CardValue.Brightness -> stringResource(Res.string.value_percent, value.percent)
    is CardValue.Measurement -> listOfNotNull(value.text, value.unit).joinToString(" ")
    is CardValue.Text -> value.value
    is CardValue.Cover -> coverText(value)
    is CardValue.Climate ->
        // A water heater card leads with the tank temperature; its chip and inlet line say the rest.
        if (kind == Kind.WATER_HEATER) value.current?.let { "$it°" } ?: stringResource(Res.string.value_none)
        // The setpoint is what the user controls, so it leads; the room temperature only stands in when there is none.
        else listOfNotNull((value.target ?: value.current)?.let { "$it°" }, value.mode?.let { modeLabel(it) })
            .joinToString(" · ")
            .ifEmpty { stringResource(Res.string.value_none) }
    is CardValue.Alarm -> stringResource(alarmLabel(value.state))
    is CardValue.MediaPlayer ->
        if (value.state == "idle") stringResource(Res.string.media_nothing_playing)
        else listOfNotNull(value.title, value.artist).joinToString(" · ").ifEmpty { stringResource(Res.string.value_none) }
    is CardValue.Vacuum ->
        listOfNotNull(value.state?.let { stringResource(vacuumStateLabel(it)) }, value.battery?.let { stringResource(Res.string.value_percent, it) })
            .joinToString(" · ")
            .ifEmpty { stringResource(Res.string.value_none) }
    is CardValue.CoverGroup -> when {
        value.moving == CoverState.OPENING -> stringResource(Res.string.value_cover_opening)
        value.moving == CoverState.CLOSING -> stringResource(Res.string.value_cover_closing)
        value.open == value.total -> stringResource(Res.string.value_group_all_open)
        value.open == 0 -> stringResource(Res.string.value_group_all_closed)
        else -> stringResource(Res.string.value_group_open_closed, value.open, value.total - value.open)
    }
    is CardValue.LightGroup -> when (value.onCount) {
        value.total -> stringResource(Res.string.value_group_all_on)
        0 -> stringResource(Res.string.value_group_all_off)
        else -> stringResource(Res.string.value_group_lights_on, value.onCount, value.total)
    }
    is CardValue.Contact -> stringResource(
        when (value.deviceClass) {
            in OPENING_CLASSES -> if (value.on) Res.string.value_contact_open else Res.string.value_contact_closed
            in MOTION_CLASSES -> if (value.on) Res.string.value_motion_detected else Res.string.value_motion_clear
            else -> if (value.on) Res.string.value_on else Res.string.value_off
        },
    )
}

/** A group's custom name, else "Shutters" / "Lights" (room groups spec §3). */
@Composable
internal fun groupName(card: CardUi): String = card.name.ifBlank {
    stringResource(if (card.kind == Kind.COVER) Res.string.group_default_covers else Res.string.group_default_lights)
}

@Composable
private fun coverText(value: CardValue.Cover): String {
    val position = value.position
    return when {
        value.state == CoverState.OPENING -> stringResource(Res.string.value_cover_opening)
        value.state == CoverState.CLOSING -> stringResource(Res.string.value_cover_closing)
        value.state == CoverState.CLOSED || position == 0 -> stringResource(Res.string.value_cover_closed)
        position != null && position in 1..99 -> stringResource(Res.string.value_cover_position, position)
        value.state == CoverState.OPEN || position == 100 -> stringResource(Res.string.value_cover_open)
        value.state == CoverState.STOPPED -> stringResource(Res.string.value_cover_stopped)
        else -> stringResource(Res.string.value_none)
    }
}

@Composable
internal fun fanModeLabel(mode: String): String = when (mode) {
    "auto" -> stringResource(Res.string.mode_auto)
    "silence" -> stringResource(Res.string.fan_quiet)
    "low" -> stringResource(Res.string.fan_low)
    "medium" -> stringResource(Res.string.fan_medium)
    "high" -> stringResource(Res.string.fan_high)
    else -> mode
}

@Composable
internal fun modeLabel(mode: String): String = when (mode) {
    "off" -> stringResource(Res.string.mode_off)
    "cool" -> stringResource(Res.string.mode_cool)
    "heat" -> stringResource(Res.string.mode_heat)
    "auto" -> stringResource(Res.string.mode_auto)
    "dry" -> stringResource(Res.string.mode_dry)
    "fan_only" -> stringResource(Res.string.mode_fan_only)
    else -> mode.replaceFirstChar { it.uppercase() }
}

internal fun vacuumStateLabel(state: String): StringResource = when (state) {
    "cleaning" -> Res.string.vacuum_state_cleaning
    "docked" -> Res.string.vacuum_state_docked
    "paused" -> Res.string.vacuum_state_paused
    "returning" -> Res.string.vacuum_state_returning
    "error" -> Res.string.vacuum_state_error
    else -> Res.string.vacuum_state_idle
}

internal fun vacuumActionLabel(action: String): StringResource = when (action) {
    "pause" -> Res.string.vacuum_pause
    "stop" -> Res.string.vacuum_stop
    "return_to_base" -> Res.string.vacuum_dock
    "locate" -> Res.string.vacuum_locate
    else -> Res.string.vacuum_start
}

@Composable
internal fun waterHeaterModeLabel(mode: String): String = when (mode) {
    "eco" -> stringResource(Res.string.water_heater_mode_eco)
    "performance" -> stringResource(Res.string.water_heater_mode_performance)
    "heat" -> stringResource(Res.string.water_heater_mode_heat)
    "off" -> stringResource(Res.string.mode_off)
    "electric" -> stringResource(Res.string.water_heater_mode_electric)
    "gas" -> stringResource(Res.string.water_heater_mode_gas)
    "heat_pump" -> stringResource(Res.string.water_heater_mode_heat_pump)
    "high_demand" -> stringResource(Res.string.water_heater_mode_high_demand)
    else -> mode.replaceFirstChar { it.uppercase() }
}

@Composable
internal fun heaterChipText(chip: HeaterChip): String = when (chip) {
    is HeaterChip.Heating -> stringResource(Res.string.water_heater_heating_power, chip.power)
    HeaterChip.Idle -> stringResource(Res.string.water_heater_idle)
    HeaterChip.Off -> stringResource(Res.string.mode_off)
    is HeaterChip.Mode -> waterHeaterModeLabel(chip.mode)
}
