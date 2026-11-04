package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gekkotron.nidoo.assist.AssistOutcome
import com.gekkotron.nidoo.assist.AssistQuestion
import com.gekkotron.nidoo.assist.AssistTurn
import com.gekkotron.nidoo.assist.QuestionKind
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
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

    /** One composition, both themes: the theme is switched in place between assertions. */
    private fun renderInBothThemes(turn: AssistTurn, text: String) {
        val theme = mutableStateOf(ThemeChoice.IVOIRE)
        val state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Turn(turn))
        rule.setContent { NidooTheme(theme.value) { sheet(state) } }
        for (choice in listOf(ThemeChoice.IVOIRE, ThemeChoice.NIGHT_GLASS)) {
            theme.value = choice
            rule.waitForIdle()
            rule.onNodeWithText(text).assertExists()
        }
    }

    @Test fun `an acted turn renders in both themes`() =
        renderInBothThemes(AssistTurn("t", "allume", AssistOutcome.ACTED, "C'est fait.", null), "C'est fait.")

    @Test fun `a choice question renders in both themes`() = renderInBothThemes(
        AssistTurn("t", "allume", AssistOutcome.ASKED, "Dans quelle pièce ?", AssistQuestion("p", QuestionKind.CHOICE, listOf("Chambre Léa", "Chambre parentale"))),
        "Chambre Léa",
    )

    @Test fun `a confirm question renders in both themes`() = renderInBothThemes(
        AssistTurn("t", "alarme", AssistOutcome.CONFIRMING, "Désarmer l'alarme ?", AssistQuestion("p", QuestionKind.CONFIRM, emptyList())),
        "Oui",
    )

    @Test fun `not understood renders in both themes`() =
        renderInBothThemes(AssistTurn("t", "bla", AssistOutcome.NOT_UNDERSTOOD, "Je n'ai pas compris.", null), "Réessayer")
}
