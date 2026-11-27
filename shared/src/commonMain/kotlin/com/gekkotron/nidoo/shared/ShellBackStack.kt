package com.gekkotron.nidoo.shared

import androidx.navigation3.runtime.NavKey
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.shared.tabs.MainTab

// The iOS shell's screens: the same key names as Android's NidooNavHost. Phase 6 merges the two hosts.
internal data object OnboardingKey : NavKey
internal data object RoomsKey : NavKey
internal data object HomeKey : NavKey
internal data object SettingsKey : NavKey

/** A Settings sub-page. */
internal data class SettingsPageKey(val page: SettingsPage) : NavKey
internal data object RoomOrderKey : NavKey
internal data object EnergyKey : NavKey
internal data object SecurityKey : NavKey
internal data object AutomationsKey : NavKey

/** History of one entity or the house; [target] is a `HistoryTarget.encode()` string. */
internal data class HistoryKey(val target: String) : NavKey

/** Energy, Security and Routines sit on top of Home, so back always returns to Home (the same rule as Android's side tabs). */
private val SIDE_TABS: Set<NavKey> = setOf(EnergyKey, SecurityKey, AutomationsKey)

/** The saved profile picks the root: Loading → none yet; none or locked → onboarding; Ready → Rooms for a new setup, else Home. */
internal fun rootFor(profile: ProfileState): NavKey? = when (profile) {
    ProfileState.Loading -> null
    ProfileState.None, ProfileState.Locked -> OnboardingKey
    is ProfileState.Ready -> if (profile.roomsPending) RoomsKey else HomeKey
}

/** Pushes [key] unless it is already on top (a double tap on the gear opens Settings once). */
internal fun push(stack: MutableList<NavKey>, key: NavKey) {
    if (stack.lastOrNull() != key) stack.add(key)
}

/** Pops [key] only while it is on top, and never the last entry (a double tap on a back arrow pops once). */
internal fun pop(stack: MutableList<NavKey>, key: NavKey) {
    if (stack.size > 1 && stack.lastOrNull() == key) stack.removeAt(stack.lastIndex)
}

/**
 * Makes [root] the only entry. The new root is added before the others go, so the stack is never empty and NavDisplay pops
 * the old entries, clearing their ViewModel stores.
 */
internal fun resetToRoot(stack: MutableList<NavKey>, root: NavKey) {
    if (stack.firstOrNull() == root) return
    stack.add(root)
    stack.removeAll { it != root }
}

internal fun selectTab(stack: MutableList<NavKey>, tab: MainTab) {
    if (tab == MainTab.HOME) {
        stack.removeAll { it in SIDE_TABS }
        return
    }
    val key = tab.key() ?: return
    if (stack.lastOrNull() == key) return
    stack.removeAll { it in SIDE_TABS }
    stack.add(key)
}

internal fun showsTabBar(top: NavKey?): Boolean = top == HomeKey || top == EnergyKey || top == SecurityKey || top == AutomationsKey

internal fun NavKey.mainTab(): MainTab? = when (this) {
    HomeKey -> MainTab.HOME
    EnergyKey -> MainTab.ENERGY
    SecurityKey -> MainTab.SECURITY
    AutomationsKey -> MainTab.AUTOMATIONS
    else -> null
}

private fun MainTab.key(): NavKey? = when (this) {
    MainTab.HOME -> HomeKey
    MainTab.ENERGY -> EnergyKey
    MainTab.SECURITY -> SecurityKey
    MainTab.AUTOMATIONS -> AutomationsKey
}
