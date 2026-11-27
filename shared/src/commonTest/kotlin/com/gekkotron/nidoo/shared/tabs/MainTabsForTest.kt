package com.gekkotron.nidoo.shared.tabs

import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileState
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTabsForTest {
    private val all = listOf(MainTab.HOME, MainTab.ENERGY, MainTab.SECURITY, MainTab.AUTOMATIONS)

    @Test
    fun everyTabForAHub() {
        val hub = ProfileState.Ready(ConnectionProfile.Direct(LocalBroker("192.168.1.91")))
        assertEquals(all, mainTabsFor(hub))
        assertEquals(all, mainTabsFor(ProfileState.Loading))
        assertEquals(all, mainTabsFor(ProfileState.None))
    }

    @Test
    fun demoHidesRoutines() {
        assertEquals(
            listOf(MainTab.HOME, MainTab.ENERGY, MainTab.SECURITY),
            mainTabsFor(ProfileState.Ready(ConnectionProfile.Demo)),
        )
    }
}
