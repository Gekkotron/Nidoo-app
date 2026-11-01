package com.gekkotron.nidoo.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.distinctColors
import com.gekkotron.nidoo.feature.home.luminanceAt
import com.gekkotron.nidoo.feature.home.renderPng
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.test.assertTrue

/** Dashboards spec: one tile of every content type, in both themes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class DashboardScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(theme: ThemeChoice, name: String, dark: Boolean) {
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground {
                    HomeScreen(
                        state = DashboardFixtures.house, onSelect = {}, onTap = {}, onSend = { _, _ -> }, now = LocalTime.NOON,
                        dashboard = DashboardFixtures.everything(),
                    )
                }
            }
        }
        val bitmap = compose.renderPng(name)
        assertTrue(bitmap.distinctColors() > 8)
        if (dark) assertTrue(bitmap.luminanceAt(4, 4) < 0.35f) else assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    @Test
    fun `dashboard grid in Night Glass`() = render(ThemeChoice.NIGHT_GLASS, "dashboard-grid-night", dark = true)

    @Test
    fun `dashboard grid in Ivoire`() = render(ThemeChoice.IVOIRE, "dashboard-grid-warm", dark = false)
}
