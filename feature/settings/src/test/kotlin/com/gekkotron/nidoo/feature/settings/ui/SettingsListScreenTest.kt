package com.gekkotron.nidoo.feature.settings.ui

import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.settings.presentation.ConnectionSummary
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.presentation.SettingsUi
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsUi
import com.gekkotron.nidoo.push.PushStatus
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class SettingsListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val pages = mutableListOf<SettingsPage>()
    private var rooms = 0
    private var backs = 0

    private val direct = ConnectionSummary.Direct("192.168.1.91:1883", tls = true, ws = false, username = "nidoo", prefix = "homeassistant", gatewayUrl = null, historyUrl = null, historyDefault = "http://192.168.1.91:8081")

    private val full = SettingsUi(
        theme = ThemePreference.IVOIRE,
        connection = direct,
        push = PushStatus.Registered(Instant.parse("2026-11-02T10:00:00Z")),
        voice = VoiceSettingsUi(enabled = true),
        link = LinkState.Connected(TransportMode.DIRECT),
        roomCount = 6,
        presenceEnabled = true,
    )

    private fun show(state: SettingsUi = full) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            SettingsListScreen(state, version = "0.12 · debug", onOpenPage = { pages += it }, onOpenRooms = { rooms++ }, onBack = { backs++ })
        }
    }

    @Test
    fun `the list shows both groups and the seven rows`() {
        show()
        for (text in listOf("House", "App", "Rooms", "Notifications", "Presence", "Voice", "Appearance", "Connection", "About")) {
            compose.onNodeWithText(text).performScrollTo().assertExists()
        }
    }

    @Test
    fun `each row reports its page, Rooms opens rooms, back reports`() {
        show()
        for (title in listOf("Appearance", "Notifications", "Presence", "Voice", "Connection", "About", "Rooms")) {
            compose.onNode(hasText(title) and hasClickAction(), useUnmergedTree = false).performScrollTo().performClick()
        }
        assertEquals(listOf(SettingsPage.APPEARANCE, SettingsPage.NOTIFICATIONS, SettingsPage.PRESENCE, SettingsPage.VOICE, SettingsPage.CONNECTION, SettingsPage.ABOUT), pages)
        assertEquals(1, rooms)
        compose.onNodeWithContentDescriptionBack().performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the statuses follow the state`() {
        show()
        for (text in listOf("6", "Registered", "Ivoire", "0.12 · debug")) {
            compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertExists()
        }
        // Presence and Voice are both on.
        compose.onAllNodesWithText("On", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `the header shows the link and the broker`() {
        show()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("192.168.1.91:1883", substring = true, useUnmergedTree = true).assertExists()
        // "Online" twice: the header and the Connection row.
        compose.onAllNodesWithText("Online", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `the demo house shows no host and no push word`() {
        show(full.copy(connection = ConnectionSummary.Demo, push = PushStatus.Demo))
        compose.onAllNodesWithText("Demo house", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Online", useUnmergedTree = true).assertCountEquals(0)
        for (word in listOf("Registered", "Waiting", "Failed", "Not available in demo")) {
            compose.onAllNodesWithText(word, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun `no profile and no rooms leave empty statuses and do not crash`() {
        show(SettingsUi())
        compose.onNodeWithText("Rooms").assertExists()
        compose.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Online", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Connecting", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `voice reads Off when the phone cannot listen, as its page's toggle does`() {
        show(full.copy(voice = full.voice.copy(availability = SpeechAvailability.Status.NoOnDevice)))
        compose.onNode(hasText("Voice") and hasText("Off") and hasClickAction()).assertExists()
    }

    @Test
    fun `the whole row is one button whose label carries the status`() {
        show()
        compose.onNode(hasText("Presence") and hasText("On") and hasClickAction()).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionBack() =
        onNode(androidx.compose.ui.test.hasContentDescription("Back"))
}
