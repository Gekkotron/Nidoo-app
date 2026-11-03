package com.gekkotron.nidoo.navigation

import androidx.navigation3.runtime.NavKey
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.AppLink
import org.junit.Test
import kotlin.test.assertEquals

class AlertLinkTest {
    @Test
    fun `nothing pending does nothing`() {
        assertEquals(AlertLinkAction.NONE, alertLinkAction(HomeKey, null))
    }

    @Test
    fun `a pending link opens once Home is the root`() {
        assertEquals(AlertLinkAction.OPEN, alertLinkAction(HomeKey, "a1"))
    }

    @Test
    fun `a pending link waits while rooms or the notification step are still ahead`() {
        assertEquals(AlertLinkAction.WAIT, alertLinkAction(RoomsKey, "a1"))
        assertEquals(AlertLinkAction.WAIT, alertLinkAction(NotificationsKey, "a1"))
    }

    @Test
    fun `a pending link is dropped on onboarding, there is no house to show it in`() {
        assertEquals(AlertLinkAction.DROP, alertLinkAction(OnboardingKey, "a1"))
    }

    @Test
    fun `screens without a tab bar keep the full insets, so the list clears the navigation bar`() {
        val tabs = WindowInsets(top = 1.dp)
        val full = WindowInsets(bottom = 48.dp)
        assertEquals(tabs, screenInsets(HomeKey, tabs, full))
        assertEquals(full, screenInsets(AlertsKey, tabs, full))
    }

    @Test
    fun `widget links follow the same rules as alerts`() {
        assertEquals(AlertLinkAction.OPEN, linkAction(HomeKey, AppLink.History("t")))
        assertEquals(AlertLinkAction.OPEN, linkAction(HomeKey, AppLink.Settings()))
        assertEquals(AlertLinkAction.WAIT, linkAction(RoomsKey, AppLink.Settings()))
        assertEquals(AlertLinkAction.DROP, linkAction(OnboardingKey, AppLink.History("t")))
        assertEquals(AlertLinkAction.NONE, linkAction(HomeKey, null))
    }

    @Test
    fun `each link opens its screen`() {
        assertEquals(listOf<NavKey>(AlertDetailKey("a1")), linkDestinations(AppLink.Alert("a1")))
        assertEquals(listOf<NavKey>(HistoryKey("entity:t")), linkDestinations(AppLink.History("t")))
        assertEquals(listOf<NavKey>(SettingsKey), linkDestinations(AppLink.Settings()))
    }

    @Test
    fun `a settings link with a page opens the page over the list, so back returns to the list`() {
        assertEquals(listOf<NavKey>(SettingsKey, SettingsPageKey(SettingsPage.CONNECTION)), linkDestinations(AppLink.Settings(SettingsPage.CONNECTION)))
    }

    @Test
    fun `a settings page key survives the back stack's save and restore`() {
        val key = SettingsPageKey(SettingsPage.VOICE)
        val json = kotlinx.serialization.json.Json.encodeToString(SettingsPageKey.serializer(), key)
        assertEquals(key, kotlinx.serialization.json.Json.decodeFromString(SettingsPageKey.serializer(), json))
    }
}
