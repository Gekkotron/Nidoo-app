package com.gekkotron.nidoo.designsystem.alarm

import com.gekkotron.nidoo.designsystem.resources.*
import org.jetbrains.compose.resources.StringResource
import com.gekkotron.nidoo.model.AlarmMode
import com.gekkotron.nidoo.model.AlarmState

/** The short state, for cards ("Armed · Away"). */
fun alarmLabel(state: AlarmState): StringResource = when (state) {
    AlarmState.DISARMED -> Res.string.alarm_disarmed
    AlarmState.ARMED_HOME -> Res.string.alarm_armed_home
    AlarmState.ARMED_AWAY -> Res.string.alarm_armed_away
    AlarmState.ARMED_NIGHT -> Res.string.alarm_armed_night
    AlarmState.ARMED_VACATION -> Res.string.alarm_armed_vacation
    AlarmState.ARMED_CUSTOM_BYPASS -> Res.string.alarm_armed_custom
    AlarmState.PENDING -> Res.string.alarm_pending
    AlarmState.ARMING -> Res.string.alarm_arming
    AlarmState.DISARMING -> Res.string.alarm_disarming
    AlarmState.TRIGGERED -> Res.string.alarm_triggered
}

/** The hero's state line: the delays get their meaning spelled out (security spec §4.2). */
fun alarmHeroLabel(state: AlarmState): StringResource = when (state) {
    AlarmState.ARMING -> Res.string.alarm_exit_delay
    AlarmState.PENDING -> Res.string.alarm_entry_delay
    else -> alarmLabel(state)
}

fun armLabel(mode: AlarmMode): StringResource = when (mode) {
    AlarmMode.HOME -> Res.string.alarm_arm_home
    AlarmMode.AWAY -> Res.string.alarm_arm_away
    AlarmMode.NIGHT -> Res.string.alarm_arm_night
    AlarmMode.VACATION -> Res.string.alarm_arm_vacation
    AlarmMode.CUSTOM_BYPASS -> Res.string.alarm_arm_custom
}
