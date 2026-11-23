package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals

/** Media player spec, Android "Home card": cover, title, artist, one play/pause button, tinted by the cover. */
@RunWith(AndroidJUnit4::class)
class MediaPlayerCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<Command>()

    private fun card(
        state: StateValue.MediaPlayer,
        features: List<String> = listOf("play", "pause", "next", "previous"),
    ): CardUi {
        val player = testEntity("m", Kind.MEDIA_PLAYER, "Music", capabilities = Capabilities(mediaFeatures = features))
        return HomeUiBuilder.build(
            mapOf("m" to player), mapOf("m" to EntityState("m", state)), emptyList(), RoomFilter.All, LinkState.Connecting, Locale.US,
        ).card("m")!!
    }

    private fun show(card: CardUi) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) { EntityCard(card, onClick = {}, onLongClick = {}, onSend = { sent += it }) }
    }

    private fun playing(color: String? = "#3d2466") =
        StateValue.MediaPlayer("playing", "Song", "Band", "Album", null, color, 200.0, 10.0, null)

    @Test
    fun `playing shows title and pause`() {
        show(card(playing()))
        compose.onNodeWithText("Song", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Band", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(listOf<Command>(Command.MediaAction("pause")), sent)
    }

    @Test
    fun `paused shows play`() {
        show(card(playing().copy(state = "paused")))
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(listOf<Command>(Command.MediaAction("play")), sent)
    }

    @Test
    fun `idle shows nothing playing and play sends play`() {
        show(card(StateValue.MediaPlayer("idle", null, null, null, null, null, null, null, null)))
        compose.onNodeWithText("Nothing playing", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(listOf<Command>(Command.MediaAction("play")), sent)
    }

    @Test
    fun `no colour keeps the plain card`() {
        show(card(playing(color = null)))
        compose.onNodeWithTag("media-tint", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a colour tints the card`() {
        show(card(playing()))
        compose.onNodeWithTag("media-tint", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `missing pause hides the button`() {
        show(card(playing(), features = listOf("play", "next")))
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }
}
