package com.gekkotron.nidoo.shared

import androidx.navigation3.runtime.NavKey
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.shared.tabs.MainTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellBackStackTest {
    @Test
    fun pushSkipsTheKeyOnTop() {
        val stack = mutableListOf<NavKey>(HomeKey)
        push(stack, SettingsKey)
        push(stack, SettingsKey)
        assertEquals(listOf<NavKey>(HomeKey, SettingsKey), stack)
    }

    @Test
    fun popOnlyPopsTheTopAndNeverTheRoot() {
        val stack = mutableListOf<NavKey>(HomeKey, SettingsKey)
        pop(stack, HomeKey)
        assertEquals(listOf<NavKey>(HomeKey, SettingsKey), stack)
        pop(stack, SettingsKey)
        assertEquals(listOf<NavKey>(HomeKey), stack)
        pop(stack, HomeKey)
        assertEquals(listOf<NavKey>(HomeKey), stack)
    }

    @Test
    fun resetToRootDropsEverythingElse() {
        val stack = mutableListOf<NavKey>(HomeKey, SettingsKey, SettingsPageKey(SettingsPage.CONNECTION))
        resetToRoot(stack, OnboardingKey)
        assertEquals(listOf<NavKey>(OnboardingKey), stack)
    }

    @Test
    fun rootFollowsTheProfile() {
        val ready = ProfileState.Ready(ConnectionProfile.Demo)
        assertNull(rootFor(ProfileState.Loading))
        assertEquals(OnboardingKey, rootFor(ProfileState.None))
        assertEquals(OnboardingKey, rootFor(ProfileState.Locked))
        assertEquals(RoomsKey, rootFor(ready.copy(roomsPending = true)))
        assertEquals(HomeKey, rootFor(ready.copy(notificationsPending = true)))
    }

    @Test
    fun energyTabSelectsOnceAndHomePopsIt() {
        val stack = mutableListOf<NavKey>(HomeKey)
        selectTab(stack, MainTab.ENERGY)
        assertEquals(listOf<NavKey>(HomeKey, EnergyKey), stack)
        selectTab(stack, MainTab.ENERGY)
        assertEquals(listOf<NavKey>(HomeKey, EnergyKey), stack)
        selectTab(stack, MainTab.HOME)
        assertEquals(listOf<NavKey>(HomeKey), stack)
    }

    @Test
    fun historyHidesTheTabBar() {
        assertTrue(showsTabBar(HomeKey))
        assertTrue(showsTabBar(EnergyKey))
        assertFalse(showsTabBar(HistoryKey("x")))
        assertFalse(showsTabBar(SettingsKey))
    }
}
