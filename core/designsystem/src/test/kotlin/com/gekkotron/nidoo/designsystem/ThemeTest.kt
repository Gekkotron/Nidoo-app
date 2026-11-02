package com.gekkotron.nidoo.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun colorsFor(choice: ThemeChoice): NidooColors {
        lateinit var captured: NidooColors
        compose.setContent { NidooTheme(choice) { captured = NidooTheme.colors } }
        compose.waitForIdle()
        return captured
    }

    @Test
    fun `night glass is dark`() = assertEquals(NightGlassColors, colorsFor(ThemeChoice.NIGHT_GLASS))

    @Test
    fun `ivoire is light`() = assertEquals(IvoireColors, colorsFor(ThemeChoice.IVOIRE))

    @Test
    @Config(qualifiers = "night")
    fun `system follows a dark system`() = assertEquals(NightGlassColors, colorsFor(ThemeChoice.SYSTEM))

    @Test
    @Config(qualifiers = "notnight")
    fun `system follows a light system`() = assertEquals(IvoireColors, colorsFor(ThemeChoice.SYSTEM))

    @Test
    fun `bothThemesHaveFiveEnergyConsumerColours`() {
        assertEquals(5, IvoireColors.energyConsumers.size)
        assertEquals(5, NightGlassColors.energyConsumers.size)
    }

    @Test
    fun `ivoireEnergyConsumerColoursMatchHub`() =
        assertEquals(androidx.compose.ui.graphics.Color(0xFF35B9F0), IvoireColors.energyConsumers[0])
}
