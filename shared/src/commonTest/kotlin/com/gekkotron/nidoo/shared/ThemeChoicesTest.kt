package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.ThemeChoice
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeChoicesTest {
    @Test
    fun `each stored preference maps to the same-named choice`() {
        assertEquals(ThemeChoice.SYSTEM, ThemePreference.SYSTEM.toChoice())
        assertEquals(ThemeChoice.NIGHT_GLASS, ThemePreference.NIGHT_GLASS.toChoice())
        assertEquals(ThemeChoice.IVOIRE, ThemePreference.IVOIRE.toChoice())
    }

    @Test
    fun `the two enums list the same names`() {
        assertEquals(ThemePreference.entries.map { it.name }, ThemeChoice.entries.map { it.name })
    }
}
