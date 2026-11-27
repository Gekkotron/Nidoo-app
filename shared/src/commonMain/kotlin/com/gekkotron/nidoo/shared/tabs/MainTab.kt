package com.gekkotron.nidoo.shared.tabs

import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileState

/** The four tabs of the bottom bar, in display order. Each platform maps them to its own navigation keys. */
enum class MainTab { HOME, ENERGY, SECURITY, AUTOMATIONS }

/** The tabs a profile shows, for both hosts: the demo has no hub, so no Routines; Loading and None keep every tab. */
fun mainTabsFor(profile: ProfileState): List<MainTab> =
    if ((profile as? ProfileState.Ready)?.profile == ConnectionProfile.Demo) MainTab.entries - MainTab.AUTOMATIONS else MainTab.entries.toList()
