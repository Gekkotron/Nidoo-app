package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.ThemeChoice

/** The stored preference as the design system's choice; exhaustive, so a new value fails the build instead of crashing. */
fun ThemePreference.toChoice(): ThemeChoice = when (this) {
    ThemePreference.SYSTEM -> ThemeChoice.SYSTEM
    ThemePreference.NIGHT_GLASS -> ThemeChoice.NIGHT_GLASS
    ThemePreference.IVOIRE -> ThemeChoice.IVOIRE
}
