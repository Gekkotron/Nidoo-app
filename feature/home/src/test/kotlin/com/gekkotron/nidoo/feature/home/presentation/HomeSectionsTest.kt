package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HomeSectionsTest {
    /** Real cards, in the order given (names sort like the list). */
    private fun cards(vararg entities: Entity): List<CardUi> {
        val byId = HomeUiBuilder.build(
            entities.associateBy { it.uniqueId }, emptyMap(), listOf(Area("living", "Living room")),
            RoomFilter.Hidden, LinkState.Connected(TransportMode.DEMO), Locale.US,
        ).cards.associateBy { it.id }
        return entities.map { byId.getValue(it.uniqueId) }
    }

    private fun e(id: String, kind: Kind) = testEntity(id, kind, area = "living", ui = UiOverride(hidden = true))

    private val expected = mapOf(
        Kind.LIGHT to HomeSection.LIGHTS,
        Kind.COVER to HomeSection.SHUTTERS_CLIMATE, Kind.CLIMATE to HomeSection.SHUTTERS_CLIMATE, Kind.WATER_HEATER to HomeSection.SHUTTERS_CLIMATE,
        Kind.SWITCH to HomeSection.PLUGS_MEDIA, Kind.MEDIA_PLAYER to HomeSection.PLUGS_MEDIA, Kind.VACUUM to HomeSection.PLUGS_MEDIA, Kind.BUTTON to HomeSection.PLUGS_MEDIA,
        Kind.SENSOR to HomeSection.SENSORS, Kind.BINARY_SENSOR to HomeSection.SENSORS,
        Kind.ALARM_PANEL to HomeSection.OTHER, Kind.NUMBER to HomeSection.OTHER, Kind.SELECT to HomeSection.OTHER,
    )

    private val everyKind get() = cards(*Kind.entries.map { e(it.name.lowercase(), it) }.toTypedArray())

    @Test
    fun `every kind has a section`() {
        assertEquals(Kind.entries.toSet(), expected.keys)
        everyKind.forEach { assertEquals(it.kind.name, expected.getValue(it.kind), HomeSections.of(it)) }
    }

    @Test
    fun `a switch shown as a light is a light`() {
        val card = cards(e("plug", Kind.LIGHT).copy(protocolKind = Kind.SWITCH)).single()
        assertEquals(HomeSection.LIGHTS, HomeSections.of(card))
    }

    @Test
    fun `room puts sensors in the strip and nowhere else`() {
        val layout = HomeSections.room(cards(e("temp", Kind.SENSOR), e("lamp", Kind.LIGHT), e("door", Kind.BINARY_SENSOR), e("shutter", Kind.COVER)))
        assertEquals(listOf("temp", "door"), layout.strip.map { it.id })
        assertEquals(listOf(HomeSection.LIGHTS, HomeSection.SHUTTERS_CLIMATE), layout.sections.map { it.section })
    }

    @Test
    fun `allDevices keeps sensors as a last section and has no strip`() {
        val layout = HomeSections.allDevices(cards(e("temp", Kind.SENSOR), e("lamp", Kind.LIGHT)))
        assertTrue(layout.strip.isEmpty())
        assertEquals(listOf(HomeSection.LIGHTS, HomeSection.SENSORS), layout.sections.map { it.section })
        assertEquals(listOf("temp"), layout.sections.last().cards.map { it.id })
    }

    @Test
    fun `no card is lost or duplicated`() {
        val all = everyKind
        for (layout in listOf(HomeSections.room(all), HomeSections.allDevices(all))) {
            val placed = (layout.strip + layout.sections.flatMap { it.cards }).map { it.id }
            assertEquals(all.map { it.id }.sorted(), placed.sorted())
        }
    }

    @Test
    fun `sections come in a fixed order and keep the incoming order of their cards`() {
        val layout = HomeSections.room(cards(e("z_plug", Kind.SWITCH), e("b_lamp", Kind.LIGHT), e("a_lamp", Kind.LIGHT), e("select", Kind.SELECT), e("ac", Kind.CLIMATE)))
        assertEquals(
            listOf(HomeSection.LIGHTS, HomeSection.SHUTTERS_CLIMATE, HomeSection.PLUGS_MEDIA, HomeSection.OTHER),
            layout.sections.map { it.section },
        )
        assertEquals(listOf("b_lamp", "a_lamp"), layout.sections.first().cards.map { it.id })
    }

    @Test
    fun `a single section hides its label and two sections show theirs`() {
        assertFalse(HomeSections.room(cards(e("lamp", Kind.LIGHT), e("temp", Kind.SENSOR))).sections.single().showLabel)
        assertTrue(HomeSections.room(cards(e("lamp", Kind.LIGHT), e("plug", Kind.SWITCH))).sections.all { it.showLabel })
    }

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals(HomeLayout(emptyList(), emptyList()), HomeSections.room(emptyList()))
    }
}
