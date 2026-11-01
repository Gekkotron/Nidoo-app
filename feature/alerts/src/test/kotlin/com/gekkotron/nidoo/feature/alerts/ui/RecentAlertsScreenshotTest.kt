package com.gekkotron.nidoo.feature.alerts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.alerts.Alert
import com.gekkotron.nidoo.alerts.demo.DemoAlerts
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.alerts.distinctColors
import com.gekkotron.nidoo.feature.alerts.luminanceAt
import com.gekkotron.nidoo.feature.alerts.renderPng
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class RecentAlertsScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-07T12:00:00Z")

    private fun render(theme: ThemeChoice, name: String, alerts: List<Alert>, off: Boolean) = run {
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground { RecentAlertsSection(alerts, now, off, onTurnOn = {}, onOpen = {}, onSeeAll = {}) }
            }
        }
        compose.renderPng(name)
    }

    @Test
    fun `rows in Night Glass`() {
        val bitmap = render(ThemeChoice.NIGHT_GLASS, "recent-rows-night", DemoAlerts.all(now).take(3), off = false)
        assertTrue(bitmap.distinctColors() > 8)
        assertTrue(bitmap.luminanceAt(4, 4) < 0.35f)
    }

    @Test
    fun `rows with the banner in Ivoire`() {
        val bitmap = render(ThemeChoice.IVOIRE, "recent-banner-warm", DemoAlerts.all(now).take(3), off = true)
        assertTrue(bitmap.distinctColors() > 8)
        assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    @Test
    fun `empty in both themes`() {
        assertTrue(render(ThemeChoice.NIGHT_GLASS, "recent-empty-night", emptyList(), off = false).distinctColors() > 3)
    }
}
