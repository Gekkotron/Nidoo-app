package com.gekkotron.nidoo.designsystem.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pins the design system's strings across the move to Compose Multiplatform resources (iOS spec §7.4). */
@RunWith(AndroidJUnit4::class)
class LinkResourcesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "fr")
    fun linkPillIsFrenchOnAFrenchPhone() {
        compose.setContent { NidooTheme(ThemeChoice.NIGHT_GLASS) { LinkPill(LinkState.Connected(TransportMode.DEMO), onClick = null) } }
        compose.onNodeWithText("Démo").assertExists()
    }

    @Test
    fun apostropheIsUnescaped() {
        compose.setContent { NidooTheme(ThemeChoice.NIGHT_GLASS) { sheet(LinkState.Connected(TransportMode.DEMO)) } }
        compose.onNodeWithText("You're exploring the demo house. Nothing leaves this phone.").assertExists()
    }

    @Test
    fun reconnectingShowsAttempt() {
        compose.setContent { NidooTheme(ThemeChoice.NIGHT_GLASS) { sheet(LinkState.Reconnecting(attempt = 3)) } }
        compose.onNodeWithText("Connection lost. Reconnecting (attempt 3)…").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun sheet(link: LinkState) = LinkSheet(link, route = null, onRouteChange = {}, onOpenSettings = {}, onDismiss = {})
}
