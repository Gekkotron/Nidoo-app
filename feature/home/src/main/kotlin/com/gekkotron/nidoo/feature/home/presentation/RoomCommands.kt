package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.Command

/** What a whole-room button acts on. */
enum class RoomKind(val key: String) { LIGHT("light"), COVER("cover") }

enum class ShutterAction { STOP, CLOSE, OPEN }

/** What one tap on a room card's lights or shutters button sends (rooms design §6.3). Absolute commands only, so Retry never flips back. */
object RoomCommands {
    /** The id a room send is tracked under: one per room and kind, so lights and shutters never share a snackbar. */
    fun sendId(areaId: String, kind: RoomKind): String = "room:$areaId:${kind.key}"

    /** What a tap would do now; null when no shutter is available. */
    fun shutterAction(room: RoomCardUi): ShutterAction? {
        val shutters = room.shutters.filter { it.available }
        return when {
            shutters.isEmpty() -> null
            shutters.any { it.moving != null } -> ShutterAction.STOP
            shutters.any { it.on } -> ShutterAction.CLOSE
            else -> ShutterAction.OPEN
        }
    }

    /** Device id → command for one tap; empty when nothing would be sent. */
    fun commands(room: RoomCardUi, kind: RoomKind): Map<String, Command> = when (kind) {
        RoomKind.LIGHT -> {
            val lights = room.lights.filter { it.available }
            if (lights.any { it.on }) lights.filter { it.on }.associate { it.id to Command.TurnOff }
            else lights.associate { it.id to it.onCommand }
        }
        RoomKind.COVER -> {
            val shutters = room.shutters.filter { it.available }
            when (shutterAction(room)) {
                ShutterAction.STOP -> shutters.filter { it.moving != null }.associate { it.id to Command.Stop }
                ShutterAction.CLOSE -> shutters.filter { it.on }.associate { it.id to Command.Close }
                ShutterAction.OPEN -> shutters.associate { it.id to Command.Open }
                null -> emptyMap()
            }
        }
    }

    /** A device that would receive a command has a cached, unconfirmed value: send nothing (entity cache spec §3.6). */
    fun blockedByStale(room: RoomCardUi, kind: RoomKind): Boolean {
        val receivers = commands(room, kind).keys
        val targets = if (kind == RoomKind.LIGHT) room.lights else room.shutters
        return targets.any { it.id in receivers && it.stale }
    }
}
