package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.hubGroupCard
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Kind
import org.junit.Test
import kotlin.test.assertEquals

class GroupCommandsTest {
    private val covers = hubGroupCard(
        testEntity("a", Kind.COVER, area = "living"),
        testEntity("b", Kind.COVER, area = "living"),
        kind = Kind.COVER,
    )

    @Test
    fun `a hub group routes every command to the group entity, hub fans out`() {
        assertEquals(listOf("group_g"), GroupCommands.targets(covers))
    }

    @Test
    fun `toggle is absolute, so Retry repeats the intent`() {
        assertEquals(Command.TurnOn(), GroupCommands.toggle(covers.copy(on = false)))
        assertEquals(Command.TurnOff, GroupCommands.toggle(covers.copy(on = true)))
    }
}
