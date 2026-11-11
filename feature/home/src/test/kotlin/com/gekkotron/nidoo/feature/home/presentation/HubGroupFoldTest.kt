package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.HUB_GROUP_DEVICE_ID
import com.gekkotron.nidoo.model.HubGroup
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HubGroupFoldTest {
    private val areas = listOf(Area("living", "Living room"), Area("bedroom", "Bedroom"))

    private fun shutter(id: String, area: String = "living") =
        testEntity(id, Kind.COVER, id, area = area, capabilities = Capabilities(position = true))

    private fun hubCover(id: String, area: String = "living") =
        testEntity("group_$id", Kind.COVER, id, area = area, deviceId = HUB_GROUP_DEVICE_ID, capabilities = Capabilities(position = true))

    private fun build(entities: List<Entity>, hub: Map<String, HubGroup>, states: Map<String, EntityState> = emptyMap()) =
        HomeUiBuilder.build(
            entities.associateBy { it.uniqueId }, states, areas, RoomFilter.All,
            LinkState.Connected(TransportMode.DEMO), Locale.US, hubGroups = hub,
        )

    @Test
    fun `a hub cover group folds its members into one wide group card`() {
        val spec = HubGroup("living", Kind.COVER, "Living shutters", listOf("bay", "side"), hideMembers = true)
        val ui = build(listOf(shutter("bay"), shutter("side"), hubCover("living")), mapOf("living" to spec))

        val group = ui.cards.single { it.isHubGroup }
        assertEquals("group_living", group.id)
        assertEquals("Living shutters", group.name)
        assertTrue(group.wide)
        assertEquals(TapAction.OPEN_SHEET, group.tap)
        assertEquals(listOf("bay", "side"), group.members.map { it.id })
        // Members never show as singles beside the group card.
        assertFalse(ui.cards.any { it.id == "bay" || it.id == "side" })
    }

    @Test
    fun `the folded hub card inherits its members shared area`() {
        val spec = HubGroup("living", Kind.COVER, "Living", listOf("bay", "side"))
        val ui = build(listOf(shutter("bay"), shutter("side"), hubCover("living")), mapOf("living" to spec))
        assertEquals("living", ui.cards.single { it.isHubGroup }.areaId)
    }

    @Test
    fun `mismatched member areas fall back to Unassigned`() {
        val spec = HubGroup("mixed", Kind.COVER, "Mixed", listOf("bay", "bed"))
        val ui = build(
            listOf(shutter("bay", area = "living"), shutter("bed", area = "bedroom"), hubCover("mixed", area = "living")),
            mapOf("mixed" to spec),
        )
        assertEquals(UNASSIGNED_AREA, ui.cards.single { it.isHubGroup }.areaId)
    }

    @Test
    fun `a hub group whose entity is absent leaves things untouched`() {
        // The config topic hasn't landed yet, only the definition — don't invent a card.
        val spec = HubGroup("living", Kind.COVER, "Living", listOf("bay", "side"))
        val ui = build(listOf(shutter("bay"), shutter("side")), mapOf("living" to spec))
        // Falls back to the existing client-side room-group fold.
        val group = ui.cards.single { it.isGroup }
        assertFalse(group.isHubGroup)
        assertEquals("group:living:cover", group.id)
    }

    @Test
    fun `cover state and position aggregate from the real member states`() {
        val spec = HubGroup("living", Kind.COVER, "Living", listOf("bay", "side"))
        val states = mapOf(
            "bay" to EntityState("bay", StateValue.Cover(CoverState.OPEN, 100)),
            "side" to EntityState("side", StateValue.Cover(CoverState.CLOSED, 0)),
        )
        val ui = build(listOf(shutter("bay"), shutter("side"), hubCover("living")), mapOf("living" to spec), states)
        val group = ui.cards.single { it.isHubGroup }
        val value = group.value as CardValue.CoverGroup
        assertEquals(1, value.open)
        assertEquals(2, value.total)
        assertEquals(listOf(100, 0), value.positions)
    }

    @Test
    fun `empty hubGroups keeps the old behaviour exactly`() {
        val ui = build(listOf(shutter("bay"), shutter("side")), emptyMap())
        val group = ui.cards.single { it.isGroup }
        assertFalse(group.isHubGroup)
        assertEquals("group:living:cover", group.id)
    }

    private fun hubLight(id: String, area: String = "living") =
        testEntity("group_$id", Kind.LIGHT, id, area = area, deviceId = HUB_GROUP_DEVICE_ID)

    @Test
    fun `a hub light group folds its members into one wide light group card`() {
        val spec = HubGroup("light_living", Kind.LIGHT, "Lights", listOf("l1", "l2"), hideMembers = true)
        val states = mapOf("l1" to EntityState("l1", StateValue.Light(true)))
        val ui = build(
            listOf(testEntity("l1", Kind.LIGHT, area = "living"), testEntity("l2", Kind.LIGHT, area = "living"), hubLight("light_living")),
            mapOf("light_living" to spec),
            states,
        )
        val group = ui.cards.single { it.isHubGroup }
        assertEquals("group_light_living", group.id)
        assertEquals("Lights", group.name)
        assertEquals(Kind.LIGHT, group.kind)
        assertTrue(group.wide)
        assertEquals(CardValue.LightGroup(onCount = 1, total = 2), group.value)
        // No loose members and no client-side "Lights" group stacked on top of the hub one.
        assertEquals(listOf("group_light_living"), ui.cards.map { it.id })
    }

    @Test
    fun `hideMembers false keeps the members on Home beside the group card`() {
        // The hub's "All shutters" spans every room and asks to keep the shutters visible.
        val spec = HubGroup("cover_house", Kind.COVER, "All shutters", listOf("bay", "bed"), hideMembers = false)
        val ui = build(
            listOf(shutter("bay", area = "living"), shutter("bed", area = "bedroom"), hubCover("cover_house")),
            mapOf("cover_house" to spec),
        )
        val group = ui.cards.single { it.isHubGroup }
        assertEquals(listOf("bay", "bed"), group.members.map { it.id })
        assertTrue(ui.cards.any { it.id == "bay" })
        assertTrue(ui.cards.any { it.id == "bed" })
    }

    @Test
    fun `a visible member of one hub group still folds into a hiding hub group`() {
        val house = HubGroup("cover_house", Kind.COVER, "All shutters", listOf("bay", "side", "bed"), hideMembers = false)
        val living = HubGroup("cover_living", Kind.COVER, "Shutters", listOf("bay", "side"), hideMembers = true)
        val ui = build(
            listOf(shutter("bay"), shutter("side"), shutter("bed", area = "bedroom"), hubCover("cover_house"), hubCover("cover_living")),
            mapOf("cover_house" to house, "cover_living" to living),
        )
        assertEquals(setOf("group_cover_house", "group_cover_living"), ui.cards.filter { it.isHubGroup }.map { it.id }.toSet())
        assertFalse(ui.cards.any { it.id == "bay" || it.id == "side" })
        assertTrue(ui.cards.any { it.id == "bed" })
    }

    @Test
    fun `hub group capabilities come from the hub entity, not the members`() {
        // The hub publishes no brightness topic for the group: no slider it cannot honour.
        val spec = HubGroup("light_living", Kind.LIGHT, "Lights", listOf("l1", "l2"))
        val dimmable = Capabilities(brightness = true)
        val ui = build(
            listOf(
                testEntity("l1", Kind.LIGHT, area = "living", capabilities = dimmable),
                testEntity("l2", Kind.LIGHT, area = "living", capabilities = dimmable),
                hubLight("light_living"),
            ),
            mapOf("light_living" to spec),
        )
        assertFalse(ui.cards.single { it.isHubGroup }.capabilities.brightness)
    }

    @Test
    fun `a hub group of a kind Home cannot render as a group is left as a plain tile`() {
        val spec = HubGroup("heat", Kind.CLIMATE, "Heating", listOf("c1", "c2"))
        val ui = build(
            listOf(
                testEntity("c1", Kind.CLIMATE, area = "living"),
                testEntity("c2", Kind.CLIMATE, area = "living"),
                testEntity("group_heat", Kind.CLIMATE, area = "living", deviceId = HUB_GROUP_DEVICE_ID),
            ),
            mapOf("heat" to spec),
        )
        assertFalse(ui.cards.any { it.isHubGroup })
        assertEquals(setOf("c1", "c2", "group_heat"), ui.cards.map { it.id }.toSet())
    }
}
