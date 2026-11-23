package com.gekkotron.nidoo.shared

import androidx.navigation3.runtime.NavKey
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage

// The iOS shell's screens: the same key names as Android's NidooNavHost. Phase 6 merges the two hosts.
internal data object OnboardingKey : NavKey
internal data object RoomsKey : NavKey
internal data object HomeKey : NavKey
internal data object SettingsKey : NavKey

/** A Settings sub-page. */
internal data class SettingsPageKey(val page: SettingsPage) : NavKey
internal data object RoomOrderKey : NavKey

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
