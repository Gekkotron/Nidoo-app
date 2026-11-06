package com.gekkotron.nidoo.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.assertEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.distinctColors
import com.gekkotron.nidoo.feature.home.luminanceAt
import com.gekkotron.nidoo.feature.home.presentation.CardUi
import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.presentation.parseTint
import com.gekkotron.nidoo.feature.home.renderPng
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.test.assertTrue

/** Media player spec, Android: the card tinted, plain and idle, and the sheet tinted, in both themes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class MediaPlayerScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun card(state: StateValue.MediaPlayer): CardUi {
        val player = testEntity(
            "m", Kind.MEDIA_PLAYER, "Music", area = "living",
            capabilities = Capabilities(mediaFeatures = listOf("play", "pause", "next", "previous", "play_media")),
        )
        return HomeUiBuilder.build(
            mapOf("m" to player), mapOf("m" to EntityState("m", state)), listOf(Area("living", "Living room")),
            RoomFilter.All, LinkState.Connecting, Locale.US,
        ).card("m")!!
    }

    private val playing = StateValue.MediaPlayer("playing", "Morning Tide", "The Paper Boats", "Harbour Lights", null, "#3d2466", 214.0, 63.0, null)
    private val idle = StateValue.MediaPlayer("idle", null, null, null, null, null, null, null, null)

    private fun check(name: String, dark: Boolean) {
        val bitmap = compose.renderPng(name)
        assertTrue(bitmap.distinctColors() > 8)
        if (dark) assertTrue(bitmap.luminanceAt(4, 4) < 0.35f) else assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    private fun cards(theme: ThemeChoice) = compose.setContent {
        NidooTheme(theme) {
            NidooBackground {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntityCard(card(playing), onClick = {}, onLongClick = {}, modifier = Modifier.weight(1f))
                        EntityCard(card(playing.copy(state = "paused")), onClick = {}, onLongClick = {}, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntityCard(card(playing.copy(color = null)), onClick = {}, onLongClick = {}, modifier = Modifier.weight(1f))
                        EntityCard(card(idle), onClick = {}, onLongClick = {}, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @Test
    fun `cards in Night Glass`() {
        cards(ThemeChoice.NIGHT_GLASS)
        check("media_player_cards_night", dark = true)
    }

    @Test
    fun `cards in Ivoire`() {
        cards(ThemeChoice.IVOIRE)
        check("media_player_cards_warm", dark = false)
    }

    /** The sheet's body on the cover colour, as ControlSheet tints it, then an idle one on the theme. */
    private fun sheets(theme: ThemeChoice) = compose.setContent {
        NidooTheme(theme) {
            NidooBackground {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = parseTint(playing.color)!!, contentColor = Color.White) {
                        Column(Modifier.padding(16.dp)) { MediaPlayerControls(card(playing), onSend = {}) }
                    }
                    MediaPlayerControls(card(idle), onSend = {})
                }
            }
        }
    }

    /** White text needs 4.5:1, so the tint's bottom, where the title and artist sit, must stay dark in both themes. */
    private fun assertTintedCardStaysDark(theme: ThemeChoice) {
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground {
                    Column(Modifier.padding(16.dp)) { EntityCard(card(playing), onClick = {}, onLongClick = {}) }
                }
            }
        }
        val bounds = compose.onNodeWithTag("card-m").fetchSemanticsNode().boundsInRoot
        val bitmap = compose.renderPng("media_player_tint_${theme.name.lowercase()}")
        // Bottom right, inside the border and away from the text.
        val pixel = bitmap.getPixel((bounds.left + bounds.width * 0.85f).toInt(), (bounds.bottom - 8f).toInt())
        val luminance = Color(pixel).luminance()
        assertTrue(luminance <= 0.18f, "bottom of the tinted card is too light for white text: luminance $luminance")
    }

    @Test
    fun `the tinted card stays dark in Ivoire`() = assertTintedCardStaysDark(ThemeChoice.IVOIRE)

    @Test
    fun `the tinted card stays dark in Night Glass`() = assertTintedCardStaysDark(ThemeChoice.NIGHT_GLASS)

    /** The sheet's own buttons (History, Customize) and drag handle use the theme's primary and onSurfaceVariant. */
    @Test
    fun `the tinted sheet keeps its buttons readable in Ivoire`() {
        val tint = parseTint(playing.color)!!
        var primary = Color.Unspecified
        var handle = Color.Unspecified
        compose.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                TintedSheetColors(tint) {
                    primary = MaterialTheme.colorScheme.primary
                    handle = MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
        }
        compose.waitForIdle()
        assertTrue(contrast(primary, tint) >= 4.5, "primary on tint: ${contrast(primary, tint)}")
        assertTrue(contrast(handle, tint) >= 3.0, "drag handle on tint: ${contrast(handle, tint)}")
    }

    @Test
    fun `an untinted sheet keeps the theme`() {
        var primary = Color.Unspecified
        var themed = Color.Unspecified
        compose.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                themed = MaterialTheme.colorScheme.primary
                TintedSheetColors(null) { primary = MaterialTheme.colorScheme.primary }
            }
        }
        compose.waitForIdle()
        assertEquals(themed, primary)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b).toDouble()
    }

    @Test
    fun `sheets in Night Glass`() {
        sheets(ThemeChoice.NIGHT_GLASS)
        check("media_player_sheets_night", dark = true)
    }

    @Test
    fun `sheets in Ivoire`() {
        sheets(ThemeChoice.IVOIRE)
        check("media_player_sheets_warm", dark = false)
    }
}
