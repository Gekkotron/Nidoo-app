package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.RoomCardUi
import com.gekkotron.nidoo.feature.home.presentation.RoomCommands
import com.gekkotron.nidoo.feature.home.presentation.RoomIcon
import com.gekkotron.nidoo.feature.home.presentation.ShutterAction
import com.gekkotron.nidoo.model.CoverState

@Composable
internal fun roomName(room: RoomCardUi): String = room.name ?: stringResource(R.string.area_unassigned)

/** The room's one-line status (rooms design §6.2): temperature · humidity · lights · shutters, or the device count. */
@Composable
internal fun roomStatus(room: RoomCardUi): String {
    val lights = when {
        room.lights.isEmpty() -> null
        room.lightsOn > 0 -> pluralStringResource(R.plurals.room_lights_on, room.lightsOn, room.lightsOn)
        else -> stringResource(R.string.room_lights_off)
    }
    val shutters = when {
        room.shutters.isEmpty() -> null
        room.shuttersMoving == CoverState.OPENING -> stringResource(R.string.room_shutters_opening)
        room.shuttersMoving == CoverState.CLOSING -> stringResource(R.string.room_shutters_closing)
        room.shuttersOpen == 0 -> stringResource(R.string.room_shutters_closed)
        room.shuttersOpen == room.shutters.size -> stringResource(R.string.room_shutters_open)
        else -> stringResource(R.string.room_shutters_some, room.shuttersOpen, room.shutters.size)
    }
    val parts = listOfNotNull(room.temperature?.let { cardValueText(it) }, room.humidity?.let { cardValueText(it) }, lights, shutters)
    return if (parts.isEmpty()) pluralStringResource(R.plurals.room_devices, room.deviceCount, room.deviceCount) else parts.joinToString(" · ")
}

/** What the lights button will do, as TalkBack reads it. */
@Composable
internal fun lightsActionLabel(room: RoomCardUi): String =
    stringResource(if (room.lightsOn > 0) R.string.room_action_lights_off else R.string.room_action_lights_on, roomName(room))

/** What the shutters button will do; "open" while no shutter is available (the button is disabled then). */
@Composable
internal fun shutterActionLabel(room: RoomCardUi): String = stringResource(
    when (RoomCommands.shutterAction(room)) {
        ShutterAction.STOP -> R.string.room_action_shutters_stop
        ShutterAction.CLOSE -> R.string.room_action_shutters_close
        ShutterAction.OPEN, null -> R.string.room_action_shutters_open
    },
    roomName(room),
)

internal fun roomGlyph(icon: RoomIcon): NidooGlyph = when (icon) {
    RoomIcon.LIVING_ROOM -> NidooGlyph.SOFA
    RoomIcon.KITCHEN -> NidooGlyph.POT
    RoomIcon.BEDROOM -> NidooGlyph.BED
    RoomIcon.BATHROOM -> NidooGlyph.BATH
    RoomIcon.OFFICE -> NidooGlyph.DESK
    RoomIcon.GARAGE -> NidooGlyph.CAR
    RoomIcon.GARDEN -> NidooGlyph.LEAF
    RoomIcon.ENTRANCE -> NidooGlyph.ENTRY_DOOR
    RoomIcon.LAUNDRY -> NidooGlyph.WASHER
    RoomIcon.TOILET -> NidooGlyph.TOILET
    RoomIcon.GENERIC -> NidooGlyph.HOUSE
}
