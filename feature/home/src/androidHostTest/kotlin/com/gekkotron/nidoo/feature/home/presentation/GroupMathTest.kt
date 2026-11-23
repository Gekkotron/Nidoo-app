package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.hubGroupCard
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class GroupMathTest {
    private fun group(vararg e: Entity, states: Map<String, EntityState>) = hubGroupCard(*e, kind = e.first().kind, states = states)

    @Test
    fun `mean position counts only members with a known position`() {
        val g = group(
            testEntity("a", Kind.COVER, area = "living", capabilities = Capabilities(position = true)),
            testEntity("b", Kind.COVER, area = "living", capabilities = Capabilities(position = true)),
            testEntity("c", Kind.COVER, area = "living"),
            states = mapOf(
                "a" to EntityState("a", StateValue.Cover(CoverState.OPEN, 100)),
                "b" to EntityState("b", StateValue.Cover(CoverState.OPEN, 41)),
                "c" to EntityState("c", StateValue.Cover(CoverState.CLOSED, null)),
            ),
        )
        assertEquals(71, GroupMath.meanPosition(g))
    }

    @Test
    fun `mean brightness counts dimmable lights that are on, else full`() {
        val g = group(
            testEntity("x", Kind.LIGHT, area = "living", capabilities = Capabilities(brightness = true)),
            testEntity("y", Kind.LIGHT, area = "living", capabilities = Capabilities(brightness = true)),
            states = mapOf("x" to EntityState("x", StateValue.Light(true, 100)), "y" to EntityState("y", StateValue.Light(false, 20))),
        )
        assertEquals(100, GroupMath.meanBrightness(g))
        val off = group(
            testEntity("x", Kind.LIGHT, area = "living", capabilities = Capabilities(brightness = true)),
            testEntity("y", Kind.LIGHT, area = "living"),
            states = emptyMap(),
        )
        assertEquals(255, GroupMath.meanBrightness(off))
    }
}
