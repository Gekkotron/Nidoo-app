package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.EnergyViewPreference
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EnergyViewSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `energyViewSectionSelectsChip`() {
        var picked: EnergyViewPreference? = null
        compose.setContent { NidooTheme(ThemeChoice.IVOIRE) { EnergyViewSection(EnergyViewPreference.CLASSIC, onSelect = { picked = it }) } }
        compose.onNodeWithText("Classic").assertIsSelected()
        compose.onNodeWithText("Flux").performClick()
        assertEquals(EnergyViewPreference.FLUX, picked)
    }
}
