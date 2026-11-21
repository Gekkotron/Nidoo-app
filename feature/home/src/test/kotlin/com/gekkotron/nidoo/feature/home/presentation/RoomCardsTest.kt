package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.feature.home.withLivingHubGroups
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.HUB_GROUP_DEVICE_ID
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import java.util.Locale
import org.junit.Test

class RoomCardsTest {
    private val areas = listOf(Area("living", "Living room"), Area("kitchen", "Kitchen"), Area("garage", "Garage"))

    private fun build(entities: List<Entity>, states: Map<String, EntityState> = emptyMap(), areaList: List<Area> = areas) =
        RoomCards.build(entities, states, areaList, entities.associate { it.uniqueId to it.name }, Locale.US)

    private fun state(id: String, value: StateValue) = id to EntityState(id, value)

    @Test
    fun `one card per room with a Home device, in area order, Unassigned last`() {
        val rooms = build(
            listOf(
                testEntity("k", Kind.LIGHT, area = "kitchen"),
                testEntity("l", Kind.LIGHT, area = "living"),
                testEntity("u", Kind.SWITCH),
            ),
        )
        assertEquals(listOf("living", "kitchen", UNASSIGNED_AREA), rooms.map { it.areaId })
        assertEquals("Living room", rooms.first().name)
        assertNull(rooms.last().name)
        assertFalse(rooms.last().buttons)
        assertEquals(true, rooms.first().buttons)
    }

    @Test
    fun `a house with no areas shows one Unassigned card`() {
        val room = build(listOf(testEntity("a", Kind.LIGHT), testEntity("b", Kind.LIGHT)), areaList = emptyList()).single()
        assertFalse(room.buttons)
        assertEquals(2, room.lights.size)
    }

    @Test
    fun `lights are lights and switches shown as lights, never hidden, companions or hub group entities`() {
        val room = build(
            listOf(
                testEntity("lamp", Kind.LIGHT, area = "living"),
                testEntity("as_light", Kind.LIGHT, area = "living", ui = UiOverride(showAs = "light")).copy(protocolKind = Kind.SWITCH),
                testEntity("plug", Kind.SWITCH, area = "living"),
                testEntity("secret", Kind.LIGHT, area = "living", ui = UiOverride(hidden = true)),
                testEntity("child", Kind.LIGHT, area = "living", partOf = "lamp"),
                testEntity("group_x", Kind.LIGHT, area = "living", deviceId = HUB_GROUP_DEVICE_ID),
            ),
        ).single()
        assertEquals(listOf("as_light", "lamp"), room.lights.map { it.id })
        assertEquals(3, room.deviceCount)
    }

    @Test
    fun `a hub group member counts in its own room only`() {
        val house = withLivingHubGroups(
            listOf(testEntity("l1", Kind.LIGHT, area = "living"), testEntity("l2", Kind.LIGHT, area = "living"), testEntity("k1", Kind.LIGHT, area = "kitchen")),
        )
        val rooms = build(house.entities.values.toList())
        assertEquals(listOf("l1", "l2"), rooms.first { it.areaId == "living" }.lights.map { it.id })
        assertEquals(listOf("k1"), rooms.first { it.areaId == "kitchen" }.lights.map { it.id })
    }

    @Test
    fun `a device shown only on Security is not a target and not counted`() {
        val room = build(
            listOf(
                testEntity("lamp", Kind.LIGHT, area = "living"),
                testEntity("door", Kind.BINARY_SENSOR, area = "living", deviceClass = "door"),
            ),
        ).single()
        assertEquals(1, room.deviceCount)
    }

    @Test
    fun `a light's preset is its on command`() {
        val ui = UiOverride(lightOnPreset = "warm")
        val caps = Capabilities(brightness = true)
        val room = build(listOf(testEntity("lamp", Kind.LIGHT, area = "living", ui = ui, capabilities = caps))).single()
        assertEquals(lightOnCommand(ui, caps), room.lights.single().onCommand)
        assertEquals(Command.TurnOn(brightness = 200), room.lights.single().onCommand)
        assertNotEquals(Command.TurnOn(), room.lights.single().onCommand)
    }

    @Test
    fun `a shutter is closed at position 0 or CLOSED without position, open otherwise`() {
        val values = listOf(
            StateValue.Cover(CoverState.CLOSED, 0), StateValue.Cover(CoverState.CLOSED, null), StateValue.Cover(CoverState.OPEN, 100),
            StateValue.Cover(CoverState.STOPPED, 40), StateValue.Cover(CoverState.UNKNOWN, null), StateValue.Cover(CoverState.OPEN, 0),
        )
        val entities = values.indices.map { testEntity("c$it", Kind.COVER, area = "living") }
        val room = build(entities, values.withIndex().associate { (i, v) -> state("c$i", v) }).single()
        assertEquals(listOf(false, false, true, true, true, false), room.shutters.map { it.on })
        assertEquals(3, room.shuttersOpen)
        assertNull(room.shuttersMoving)
    }

    @Test
    fun `a moving shutter reports its direction`() {
        val room = build(listOf(testEntity("c", Kind.COVER, area = "living")), mapOf(state("c", StateValue.Cover(CoverState.CLOSING, 60)))).single()
        assertEquals(CoverState.CLOSING, room.shutters.single().moving)
        assertEquals(CoverState.CLOSING, room.shuttersMoving)
    }

    @Test
    fun `temperature and humidity are the first available sensor of that class`() {
        val room = build(
            listOf(
                testEntity("ta", Kind.SENSOR, name = "A", area = "living", deviceClass = "temperature", unit = "°C"),
                testEntity("tb", Kind.SENSOR, name = "B", area = "living", deviceClass = "temperature", unit = "°C"),
                testEntity("h", Kind.SENSOR, area = "living", deviceClass = "humidity", unit = "%"),
            ),
            mapOf(state("tb", StateValue.Numeric(21.5)), state("h", StateValue.Numeric(48.0))),
        ).single()
        assertEquals(CardValue.Measurement("21.5", "°C"), room.temperature)
        assertEquals("48", room.humidity?.text)
    }

    @Test
    fun `an unavailable sensor is not the room's temperature`() {
        val room = build(
            listOf(testEntity("t", Kind.SENSOR, area = "living", deviceClass = "temperature")),
            mapOf("t" to EntityState("t", StateValue.Numeric(20.0), available = false)),
        ).single()
        assertNull(room.temperature)
    }

    @Test
    fun counts() {
        val entities = (1..4).map { testEntity("l$it", Kind.LIGHT, area = "living") }
        val room = build(entities, mapOf(state("l1", StateValue.Light(true)), state("l2", StateValue.Light(true)), state("l3", StateValue.Light(false)))).single()
        assertEquals(2, room.lightsOn)
        assertEquals(4, room.deviceCount)
    }

    @Test
    fun `stale and unavailable states reach the targets`() {
        val room = build(
            listOf(testEntity("a", Kind.LIGHT, area = "living"), testEntity("b", Kind.LIGHT, area = "living")),
            mapOf("a" to EntityState("a", StateValue.Light(true), stale = true), "b" to EntityState("b", StateValue.Light(true), available = false)),
        ).single()
        assertEquals(listOf(true, false), room.lights.map { it.stale })
        assertEquals(listOf(true, false), room.lights.map { it.available })
        assertEquals(1, room.lightsOn)
    }
}
