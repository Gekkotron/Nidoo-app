package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class VoiceSheetScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `listening shows the pulsing mic and the partial text`() {
        val state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Listening("ouvre les vo"))
        rule.setContent { sheet(state) }
        rule.onNodeWithText("ouvre les vo").assertExists()
    }

    @Test fun `sending shows Envoi and the heard text`() {
        val state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Sending("allume le salon"))
        rule.setContent { sheet(state) }
        rule.onNodeWithText("Envoi…").assertExists()
        rule.onNodeWithText("allume le salon").assertExists()
    }
}
