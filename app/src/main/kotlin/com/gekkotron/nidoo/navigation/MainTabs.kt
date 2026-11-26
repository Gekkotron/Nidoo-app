package com.gekkotron.nidoo.navigation

import androidx.navigation3.runtime.NavKey
import com.gekkotron.nidoo.shared.tabs.MainTab

/** Energy, Security and Automations sit on top of Home and replace each other, so back always returns to Home (security spec §5). */
private val SIDE_TABS: Set<NavKey> = setOf(EnergyKey, SecurityKey, AutomationsKey)

internal fun selectTab(stack: MutableList<NavKey>, key: NavKey) {
    if (key == HomeKey) {
        stack.removeAll { it in SIDE_TABS }
        return
    }
    if (stack.lastOrNull() == key) return
    stack.removeAll { it in SIDE_TABS }
    stack.add(key)
}

/**
 * A new root (onboarding finished, "set up again") replaces the whole stack. The same root keeps it, so a
 * stack restored after rotation or a theme change stays on the Energy tab instead of jumping back to Home.
 */
internal fun resetToRoot(stack: MutableList<NavKey>, root: NavKey) {
    if (stack.firstOrNull() == root) return
    stack.add(root)
    stack.removeAll { it != root }
}

internal fun showsTabBar(top: NavKey?): Boolean = top == HomeKey || (top != null && top in SIDE_TABS)

internal fun NavKey.mainTab(): MainTab? = when (this) {
    HomeKey -> MainTab.HOME
    EnergyKey -> MainTab.ENERGY
    SecurityKey -> MainTab.SECURITY
    AutomationsKey -> MainTab.AUTOMATIONS
    else -> null
}

internal fun MainTab.key(): NavKey = when (this) {
    MainTab.HOME -> HomeKey
    MainTab.ENERGY -> EnergyKey
    MainTab.SECURITY -> SecurityKey
    MainTab.AUTOMATIONS -> AutomationsKey
}
