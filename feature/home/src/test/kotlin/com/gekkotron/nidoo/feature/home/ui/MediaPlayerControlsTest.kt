package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Column
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
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/** Media player spec, Android "Control sheet": ⏮ ▶/❚❚ ⏭ as the player allows, and the progress line. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class MediaPlayerControlsTest {
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
        NidooTheme(ThemeChoice.NIGHT_GLASS) { Column { MediaPlayerControls(card, onSend = { sent += it }) } }
    }

    private val paused = StateValue.MediaPlayer("paused", "Song", "Band", "Album", null, "#3d2466", 200.0, 50.0, null)

    @Test
    fun `sheet shows previous, play-pause and next`() {
        show(card(paused))
        compose.onNodeWithText("Song").assertExists()
        compose.onNodeWithText("Band · Album").assertExists()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf<Command>(Command.MediaAction("previous"), Command.MediaAction("play"), Command.MediaAction("next")), sent)
    }

    @Test
    fun `playing shows pause`() {
        show(card(paused.copy(state = "playing")))
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(listOf<Command>(Command.MediaAction("pause")), sent)
    }

    @Test
    fun `features without next and previous hide them`() {
        show(card(paused, features = listOf("play", "pause")))
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertExists()
    }

    @Test
    fun `progress is shown with a duration and hidden without`() {
        show(card(paused))
        compose.onNodeWithTag("media-progress").assertExists()
    }

    @Test
    fun `progress is hidden without a duration`() {
        show(card(paused.copy(durationS = null)))
        compose.onNodeWithTag("media-progress").assertDoesNotExist()
    }
}
