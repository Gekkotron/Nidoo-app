package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.settings.presentation.ConnectionSummary
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.presentation.SettingsUi
import com.gekkotron.nidoo.transport.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
// Tall enough that the connection rows sit on screen without scrolling: the dialog tests pause the clock, and a
// scrolled screen behind an open dialog never settles under Robolectric.
@Config(qualifiers = "w411dp-h1600dp")
class SettingsPagesTest {
    @get:Rule
    val compose = createComposeRule()

    private val themes = mutableListOf<ThemePreference>()
    private var setUpAgain = 0
    private var licences = 0

    private val direct = ConnectionSummary.Direct("192.168.1.91:1883", tls = true, ws = false, username = "nidoo", prefix = "homeassistant", gatewayUrl = "https://gw.example", historyUrl = null, historyDefault = "http://192.168.1.91:8081")

    private fun show(page: SettingsPage, state: SettingsUi = SettingsUi(connection = direct), route: Route? = Route.AUTO, onOpenLicences: (() -> Unit)? = { licences++ }) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            SettingsPageScreen(
                page, state, version = "0.12 · debug", route = route, onBack = {},
                onTheme = { themes += it }, onRouteChange = {}, onSetUpAgain = { setUpAgain++ },
                onOpenLicences = onOpenLicences,
                onSaveHistoryUrl = { saveHistory(it) },
            )
        }
    }

    private val savedHistory = mutableListOf<String>()
    private var historyAccepts = false
    private fun saveHistory(text: String): Boolean { savedHistory += text; return historyAccepts }

    private val savedFrigate = mutableListOf<Triple<String, String, String>>()
    private var frigateAccepts = true

    @Test
    fun `theme rows show the choice and report a pick`() {
        show(SettingsPage.APPEARANCE)
        compose.onNodeWithText("Follow the system").assertIsSelected()
        compose.onNodeWithText("Ivoire").performClick()
        assertEquals(listOf(ThemePreference.IVOIRE), themes)
    }

    @Test
    fun `connection summary shows the broker and the gateway, and the route picker`() {
        show(SettingsPage.CONNECTION)
        compose.onNodeWithText("192.168.1.91:1883").performScrollTo().assertExists()
        compose.onNodeWithText("https://gw.example").performScrollTo().assertExists()
        compose.onNodeWithText("Automatic").performScrollTo().assertIsSelected()
    }

    @Test
    fun `no gateway, no route picker`() {
        show(SettingsPage.CONNECTION, route = null)
        compose.onNodeWithText("Automatic").assertDoesNotExist()
    }

    @Test
    fun `set up again asks first, cancel does nothing and confirm calls back once`() {
        show(SettingsPage.CONNECTION)
        compose.onNodeWithText("Set up connection again").performScrollTo().performClick()
        compose.onNodeWithText("Set up again?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, setUpAgain)
        compose.onNodeWithText("Set up connection again").performScrollTo().performClick()
        compose.onNodeWithText("Set up again").performClick()
        assertEquals(1, setUpAgain)
        compose.onNodeWithText("Set up again?").assertDoesNotExist()
    }

    @Test
    fun `the licences row navigates, version is shown`() {
        show(SettingsPage.ABOUT)
        compose.onNodeWithText("Open-source licences").performScrollTo().performClick()
        compose.onNodeWithText("Nidoo 0.12 · debug").performScrollTo().assertExists()
        assertEquals(1, licences)
    }

    @Test
    fun licencesRowHiddenWithoutCallback() {
        show(SettingsPage.ABOUT, onOpenLicences = null)
        compose.onNodeWithText("Nidoo 0.12 · debug").assertExists()
        compose.onNodeWithText("Open-source licences").assertDoesNotExist()
    }

    @Test
    fun `no profile shows no connection rows and does not crash`() {
        show(SettingsPage.CONNECTION, SettingsUi(connection = null), route = null)
        compose.onNodeWithText("Broker").assertDoesNotExist()
        compose.onNodeWithText("Set up connection again").performScrollTo().assertExists()
    }

    /** Drives the paused clock and lets the dialog window attach. */
    private fun settle() {
        repeat(3) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(200)
        }
    }

    // The dialog's field recomposes once the window takes focus, and Robolectric then never reports Compose idle
    // while the dialog stays open; so the clock is driven by hand and each test ends as the dialog closes.
    private fun showHistory() {
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                SettingsPageScreen(
                    SettingsPage.CONNECTION, SettingsUi(connection = direct), version = "0.12 · debug", route = null,
                    onBack = {},
                    onSaveHistoryUrl = { saveHistory(it) },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("History service").performScrollTo()
        compose.onNodeWithText("Automatic (http://192.168.1.91:8081)").performClick()
        settle()
    }

    @Test
    fun `an automatic history url shows the derived address and tapping it opens the dialog, Cancel closes it`() {
        showHistory()
        compose.onNodeWithText("Save").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        settle()
        compose.onNodeWithText("Save").assertDoesNotExist()
        assertEquals(emptyList(), savedHistory)
    }

    @Test
    fun `an accepted save closes the dialog`() {
        historyAccepts = true
        showHistory()
        compose.onNodeWithText("Save").performClick()
        settle()
        assertEquals(listOf(""), savedHistory)
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `a refused save is reported to the caller`() {
        showHistory()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(""), savedHistory)
    }

    // The dialog body is stateless, mirrors HistoryUrlDialog's save rule, and needs no dialog window.
    private fun showContent(accept: Boolean, onDismiss: () -> Unit) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            var failed by remember { mutableStateOf(false) }
            HistoryUrlDialogContent(
                "nope", failed, hint = "http://h:8081", onTextChange = {},
                onSave = { if (accept) onDismiss() else failed = true },
                onCancel = {},
            )
        }
    }

    @Test
    fun `a refused save keeps the dialog open and shows the error`() {
        var dismissed = 0
        showContent(accept = false) { dismissed++ }
        compose.onNodeWithText("Use http(s)://host[:port], nothing after it").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Use http(s)://host[:port], nothing after it").assertExists()
        compose.onNodeWithText("Save").assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun `the dialog hints at the derived address`() {
        showContent(accept = true) {}
        compose.onNodeWithText("http://h:8081").assertExists()
    }

    @Test
    fun `an override is shown as is`() {
        show(SettingsPage.CONNECTION, SettingsUi(connection = direct.copy(historyUrl = "http://nas:9000")))
        compose.onNodeWithText("http://nas:9000").assertExists()
    }

    @Test
    fun `an accepted save dismisses`() {
        var dismissed = 0
        showContent(accept = true) { dismissed++ }
        compose.onNodeWithText("Save").performClick()
        assertEquals(1, dismissed)
    }

    // Same hand-driven clock as showHistory: an open dialog never lets Robolectric report Compose idle.
    private fun showFrigate(connection: ConnectionSummary.Direct) {
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                SettingsPageScreen(
                    SettingsPage.CONNECTION, SettingsUi(connection = connection), version = "0.12 · debug", route = null,
                    onBack = {},
                    onSaveFrigate = { u, n, p -> savedFrigate += Triple(u, n, p); frigateAccepts },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Frigate").performScrollTo()
    }

    @Test
    fun `an unset frigate shows Not set and the dialog starts from the generic default`() {
        showFrigate(direct)
        compose.onNodeWithText("Not set").performClick()
        settle()
        compose.onNodeWithText("http://frigate:8971").assertExists()
        compose.onNodeWithText("e.g. http://frigate:8971 (login) or http://frigate:5000 (no login)").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        settle()
    }

    @Test
    fun `a configured frigate shows its url and user, and save reports the three fields`() {
        showFrigate(direct.copy(frigateUrl = "http://192.168.1.91:8971", frigateUser = "admin", frigateHasPassword = true))
        compose.onNodeWithText("http://192.168.1.91:8971").performClick()
        settle()
        compose.onNodeWithText("Unchanged").assertExists()
        compose.onNodeWithText("Save").performClick()
        settle()
        assertEquals(listOf(Triple("http://192.168.1.91:8971", "admin", "")), savedFrigate)
    }

    // Stateless body, like the history one: an open dialog window never lets Robolectric go idle.
    @Test
    fun `a refused frigate save keeps the dialog open with the error`() {
        var dismissed = 0
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                var failed by remember { mutableStateOf(false) }
                FrigateDialogContent(
                    "nope", "", initialUser = "", password = "", hasPassword = false, failed = failed,
                    onUrl = {}, onUser = {}, onPassword = {},
                    onSave = { if (frigateAccepts) dismissed++ else failed = true },
                    onCancel = {},
                )
            }
        }
        frigateAccepts = false
        compose.onNodeWithText("Use http(s)://host[:port], nothing after it").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Use http(s)://host[:port], nothing after it").assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun `the Unchanged hint shows for the stored user only`() {
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                var user by remember { mutableStateOf("admin") }
                FrigateDialogContent(
                    "http://frigate:8971", user, initialUser = "admin", password = "", hasPassword = true, failed = false,
                    onUrl = {}, onUser = { user = it }, onPassword = {}, onSave = {}, onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Unchanged").assertExists()
        compose.onNodeWithText("admin").performTextInput("x")
        compose.onNodeWithText("Unchanged").assertDoesNotExist()
    }

    @Test
    fun `a frigate user without a password shows no Login line`() {
        showFrigate(direct.copy(frigateUrl = "http://192.168.1.91:8971", frigateUser = "admin", frigateHasPassword = false))
        compose.onNodeWithText("Login: admin").assertDoesNotExist()
    }

    @Test
    fun `each page has its title once and a back arrow that reports`() {
        var page by mutableStateOf(SettingsPage.APPEARANCE)
        var backs = 0
        compose.setContent {
            NidooTheme(ThemeChoice.NIGHT_GLASS) {
                SettingsPageScreen(page, SettingsUi(connection = direct), version = "0.12 · debug", route = null, onBack = { backs++ })
            }
        }
        val titles = mapOf(
            SettingsPage.APPEARANCE to "Appearance", SettingsPage.NOTIFICATIONS to "Notifications", SettingsPage.PRESENCE to "Presence",
            SettingsPage.VOICE to "Voice", SettingsPage.CONNECTION to "Connection", SettingsPage.ABOUT to "About",
        )
        for ((p, title) in titles) {
            page = p
            compose.waitForIdle()
            compose.onNodeWithText(title).assertExists()
            compose.onNode(hasContentDescription("Back")).performClick()
        }
        assertEquals(titles.size, backs)
    }
}
