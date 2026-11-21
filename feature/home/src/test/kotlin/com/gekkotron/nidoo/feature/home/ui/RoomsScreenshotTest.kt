package com.gekkotron.nidoo.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.distinctColors
import com.gekkotron.nidoo.feature.home.luminanceAt
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.renderPng
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rooms design §7: the Rooms view and a room view, both themes, phone and a wide window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1200dp-xxhdpi")
class RoomsScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(theme: ThemeChoice, selected: RoomFilter, name: String, dark: Boolean) {
        val state = RoomsFixtures.house(selected)
        assertEquals(selected, state.selected)
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground {
                    HomeScreen(state = state, onSelect = {}, onTap = {}, onSend = { _, _ -> }, now = LocalTime.NOON)
                }
            }
        }
        val bitmap = compose.renderPng(name)
        assertTrue(bitmap.distinctColors() > 8)
        if (dark) assertTrue(bitmap.luminanceAt(4, 4) < 0.35f) else assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    @Test
    fun `Rooms in Night Glass`() = render(ThemeChoice.NIGHT_GLASS, RoomFilter.Rooms, "rooms-night", dark = true)

    @Test
    fun `Rooms in Ivoire`() = render(ThemeChoice.IVOIRE, RoomFilter.Rooms, "rooms-warm", dark = false)

    @Test
    fun `a room in Night Glass`() = render(ThemeChoice.NIGHT_GLASS, RoomFilter.Area("living"), "room-night", dark = true)

    @Test
    fun `a room in Ivoire`() = render(ThemeChoice.IVOIRE, RoomFilter.Area("living"), "room-warm", dark = false)

    @Test
    @Config(qualifiers = "w900dp-h1000dp-xhdpi")
    fun `Rooms on a wide window`() = render(ThemeChoice.NIGHT_GLASS, RoomFilter.Rooms, "rooms-wide-night", dark = true)

    @Test
    @Config(qualifiers = "w900dp-h1000dp-xhdpi")
    fun `a room on a wide window`() = render(ThemeChoice.NIGHT_GLASS, RoomFilter.Area("living"), "room-wide-night", dark = true)
}
