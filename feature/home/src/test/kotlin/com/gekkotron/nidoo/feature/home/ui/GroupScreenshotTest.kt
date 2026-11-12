package com.gekkotron.nidoo.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
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
import com.gekkotron.nidoo.feature.home.presentation.HomeUiState
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.renderPng
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.feature.home.withLivingHubGroups
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertTrue

/** Room groups spec §10: the group cards and sheets in both themes. Sheets live in their own window, so their content is drawn directly. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class GroupScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val house = withLivingHubGroups(
        listOf(
            testEntity("bay", Kind.COVER, "Bay window", area = "living", capabilities = Capabilities(position = true)),
            testEntity("side", Kind.COVER, "Side window", area = "living", capabilities = Capabilities(position = true)),
            testEntity("patio", Kind.COVER, "Patio door", area = "living"),
            testEntity("l1", Kind.LIGHT, "Ceiling", area = "living", capabilities = Capabilities(brightness = true)),
            testEntity("l2", Kind.LIGHT, "Floor lamp", area = "living"),
            testEntity("temp", Kind.SENSOR, "Temperature", area = "living", unit = "°C"),
        ),
    )

    private fun state(): HomeUiState = HomeUiBuilder.build(
        house.entities,
        mapOf(
            "bay" to EntityState("bay", StateValue.Cover(CoverState.OPEN, 100)),
            "side" to EntityState("side", StateValue.Cover(CoverState.OPEN, 80)),
            "patio" to EntityState("patio", StateValue.Cover(CoverState.CLOSED, null)),
            "l1" to EntityState("l1", StateValue.Light(true, 200)),
            "temp" to EntityState("temp", StateValue.Numeric(21.4)),
        ),
        listOf(Area("living", "Living room")), RoomFilter.Area("living"), LinkState.Connected(TransportMode.DEMO), Locale.US, hubGroups = house.hubGroups,
    )

    private fun group(kind: Kind): CardUi = state().cards.single { it.isGroup && it.kind == kind }

    private fun assertTheme(name: String, dark: Boolean) {
        val bitmap = compose.renderPng(name)
        assertTrue(bitmap.distinctColors() > 8)
        if (dark) assertTrue(bitmap.luminanceAt(4, 4) < 0.35f) else assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    private fun home(theme: ThemeChoice) = compose.setContent {
        NidooTheme(theme) { NidooBackground { HomeScreen(state = state(), onSelect = {}, onTap = {}, onSend = { _, _ -> }, now = LocalTime.NOON) } }
    }

    private fun sheet(theme: ThemeChoice, kind: Kind) = compose.setContent {
        NidooTheme(theme) {
            NidooBackground {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GroupControls(group(kind), onSend = { _, _ -> }, onSendGroup = { _, _ -> }, onCustomizeMember = {})
                }
            }
        }
    }

    @Test
    fun `home with groups in Night Glass`() {
        home(ThemeChoice.NIGHT_GLASS)
        assertTheme("groups-home-night", dark = true)
    }

    @Test
    fun `home with groups in Ivoire`() {
        home(ThemeChoice.IVOIRE)
        assertTheme("groups-home-warm", dark = false)
    }

    @Test
    fun `shutter group sheet in Night Glass`() {
        sheet(ThemeChoice.NIGHT_GLASS, Kind.COVER)
        assertTheme("groups-sheet-covers-night", dark = true)
    }

    @Test
    fun `light group sheet in Ivoire`() {
        sheet(ThemeChoice.IVOIRE, Kind.LIGHT)
        assertTheme("groups-sheet-lights-warm", dark = false)
    }
}
