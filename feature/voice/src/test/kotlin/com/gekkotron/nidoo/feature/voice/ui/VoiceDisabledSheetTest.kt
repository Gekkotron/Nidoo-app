package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.voice.ErrorKind
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class VoiceDisabledSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `voice off explains it and offers Settings`() {
        var opened = false
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                VoiceSheet(
                    state = MutableStateFlow(VoiceSessionState.Error(ErrorKind.DISABLED)),
                    onPick = {}, onConfirm = {}, onCode = {}, onCancel = {},
                    onOpenSettings = { opened = true },
                )
            }
        }
        rule.onNodeWithText("L'assistant vocal est désactivé. Activez-le dans les Réglages.").assertExists()
        rule.onNodeWithText("Ouvrir les Réglages").performClick()
        assertTrue(opened)
    }

    @Test fun `other errors have no Settings button`() {
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                VoiceSheet(state = MutableStateFlow(VoiceSessionState.Error(ErrorKind.SILENCE)), onPick = {}, onConfirm = {}, onCode = {}, onCancel = {})
            }
        }
        rule.onNodeWithText("Ouvrir les Réglages").assertDoesNotExist()
    }
}
