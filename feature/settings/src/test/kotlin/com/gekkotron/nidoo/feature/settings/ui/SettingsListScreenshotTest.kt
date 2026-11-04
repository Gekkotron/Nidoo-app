package com.gekkotron.nidoo.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.settings.distinctColors
import com.gekkotron.nidoo.feature.settings.luminanceAt
import com.gekkotron.nidoo.feature.settings.presentation.ConnectionSummary
import com.gekkotron.nidoo.feature.settings.presentation.SettingsUi
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsUi
import com.gekkotron.nidoo.feature.settings.renderPng
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Writes `build/screenshots/settings-list-*.png` to check by eye that the whole list fits one phone screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class SettingsListScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = SettingsUi(
        theme = ThemePreference.NIGHT_GLASS,
        connection = ConnectionSummary.Direct("192.168.1.91:1883", tls = true, ws = false, username = "nidoo", prefix = "homeassistant", gatewayUrl = null, historyUrl = null, historyDefault = "http://192.168.1.91:8081"),
        push = PushStatus.Registered(Instant.parse("2026-11-02T10:00:00Z")),
        voice = VoiceSettingsUi(enabled = true),
        link = LinkState.Connected(TransportMode.DIRECT),
        roomCount = 6,
        presenceEnabled = true,
    )

    private fun render(theme: ThemeChoice, name: String, dark: Boolean) {
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground { SettingsListScreen(state, version = "1.12.0", onOpenPage = {}, onOpenRooms = {}, onBack = {}) }
            }
        }
        val bitmap = compose.renderPng(name)
        assertTrue(bitmap.distinctColors() > 20)
        if (dark) assertTrue(bitmap.luminanceAt(4, 4) < 0.35f) else assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    @Test
    fun `settings list renders in Night Glass`() = render(ThemeChoice.NIGHT_GLASS, "settings-list-night", dark = true)

    @Test
    fun `settings list renders in Ivoire`() = render(ThemeChoice.IVOIRE, "settings-list-ivoire", dark = false)
}
