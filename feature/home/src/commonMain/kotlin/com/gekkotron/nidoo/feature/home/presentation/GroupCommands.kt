package com.gekkotron.nidoo.feature.home.presentation

/** Where a command to a hub group goes (hub cover groups spec §2.4). */
object GroupCommands {
    /** The hub owns fanout: one command to the group entity. */
    fun targets(group: CardUi): List<String> = listOf(group.id)

    /** Absolute, so Retry repeats the intent instead of flipping it back. */
    fun toggle(group: CardUi): com.gekkotron.nidoo.model.Command =
        if (group.on) com.gekkotron.nidoo.model.Command.TurnOff else com.gekkotron.nidoo.model.Command.TurnOn()
}
