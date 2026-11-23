package com.gekkotron.nidoo.feature.settings.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.settings.presentation.VoiceSettingsUi
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class VoiceSectionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(availability: SpeechAvailability.Status) = compose.setContent {
        NidooTheme(ThemeChoice.IVOIRE) { VoiceSection(VoiceSettingsUi(availability = availability), onToggle = {}, showTitle = false) }
    }

    @Test
    fun voiceSection_ready_shows_only_the_toggle_light() {
        show(SpeechAvailability.Status.Ready)
        compose.onNodeWithText("Voice commands").assertExists()
        compose.onNodeWithText("Response voice").assertDoesNotExist()
        compose.onNodeWithText("Voice shortcuts").assertDoesNotExist()
    }

    @Test
    fun voiceSection_without_on_device_recognizer_shows_the_reason_light() {
        show(SpeechAvailability.Status.NoOnDevice)
        compose.onNodeWithText("On-device speech recognition is not available on this phone.").assertExists()
        compose.onNodeWithText("Response voice").assertDoesNotExist()
    }
}
