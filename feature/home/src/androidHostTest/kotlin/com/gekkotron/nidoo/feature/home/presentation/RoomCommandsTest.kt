package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CoverState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCommandsTest {
    private fun t(
        id: String,
        on: Boolean,
        available: Boolean = true,
        stale: Boolean = false,
        moving: CoverState? = null,
        onCommand: Command.TurnOn = Command.TurnOn(),
    ) = RoomTarget(id, id, available, stale, on, moving, onCommand)

    private fun room(lights: List<RoomTarget> = emptyList(), shutters: List<RoomTarget> = emptyList()) =
        RoomCardUi("living", "Living room", RoomIcon.LIVING_ROOM, null, null, lights, shutters, lights.size + shutters.size, buttons = true)

    @Test
    fun `any light on turns off the lights that are on`() {
        val r = room(lights = listOf(t("a", true), t("b", false), t("c", true)))
        assertEquals(mapOf<String, Command>("a" to Command.TurnOff, "c" to Command.TurnOff), RoomCommands.commands(r, RoomKind.LIGHT))
    }

    @Test
    fun `no light on turns each one on with its own command`() {
        val r = room(lights = listOf(t("a", false, onCommand = Command.TurnOn(brightness = 128)), t("b", false)))
        assertEquals(mapOf<String, Command>("a" to Command.TurnOn(brightness = 128), "b" to Command.TurnOn()), RoomCommands.commands(r, RoomKind.LIGHT))
    }

    @Test
    fun `unavailable lights are skipped and do not count as on`() {
        val r = room(lights = listOf(t("a", true, available = false), t("b", false)))
        assertEquals(mapOf<String, Command>("b" to Command.TurnOn()), RoomCommands.commands(r, RoomKind.LIGHT))
    }

    @Test
    fun `a room whose only lights are unavailable sends nothing`() {
        val r = room(lights = listOf(t("a", true, available = false, stale = true)))
        assertTrue(RoomCommands.commands(r, RoomKind.LIGHT).isEmpty())
        assertFalse(RoomCommands.blockedByStale(r, RoomKind.LIGHT))
    }

    @Test
    fun `a moving shutter stops the moving ones only`() {
        val r = room(shutters = listOf(t("a", true, moving = CoverState.CLOSING), t("b", true)))
        assertEquals(ShutterAction.STOP, RoomCommands.shutterAction(r))
        assertEquals(mapOf<String, Command>("a" to Command.Stop), RoomCommands.commands(r, RoomKind.COVER))
    }

    @Test
    fun `any open shutter closes those not closed`() {
        val r = room(shutters = listOf(t("a", true), t("b", false)))
        assertEquals(ShutterAction.CLOSE, RoomCommands.shutterAction(r))
        assertEquals(mapOf<String, Command>("a" to Command.Close), RoomCommands.commands(r, RoomKind.COVER))
    }

    @Test
    fun `all closed opens all`() {
        val r = room(shutters = listOf(t("a", false), t("b", false)))
        assertEquals(ShutterAction.OPEN, RoomCommands.shutterAction(r))
        assertEquals(mapOf<String, Command>("a" to Command.Open, "b" to Command.Open), RoomCommands.commands(r, RoomKind.COVER))
    }

    @Test
    fun `no available shutter has no action`() {
        val r = room(shutters = listOf(t("a", true, available = false)))
        assertNull(RoomCommands.shutterAction(r))
        assertTrue(RoomCommands.commands(r, RoomKind.COVER).isEmpty())
    }

    @Test
    fun `a room without shutters has no action`() {
        assertNull(RoomCommands.shutterAction(room()))
        assertTrue(RoomCommands.commands(room(), RoomKind.COVER).isEmpty())
        assertTrue(RoomCommands.commands(room(), RoomKind.LIGHT).isEmpty())
    }

    @Test
    fun `a stale receiver blocks the send`() {
        val r = room(lights = listOf(t("a", true, stale = true), t("b", false)))
        assertTrue(RoomCommands.blockedByStale(r, RoomKind.LIGHT))
    }

    @Test
    fun `a stale device that would not receive the command does not block`() {
        val r = room(lights = listOf(t("a", true), t("b", false, stale = true)))
        assertFalse(RoomCommands.blockedByStale(r, RoomKind.LIGHT))
    }

    @Test
    fun `send ids differ per kind`() {
        assertEquals("room:living:light", RoomCommands.sendId("living", RoomKind.LIGHT))
        assertEquals("room:living:cover", RoomCommands.sendId("living", RoomKind.COVER))
    }
}
