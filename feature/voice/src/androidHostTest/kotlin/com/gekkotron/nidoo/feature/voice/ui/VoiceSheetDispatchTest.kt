package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gekkotron.nidoo.assist.AssistOutcome
import com.gekkotron.nidoo.assist.AssistQuestion
import com.gekkotron.nidoo.assist.AssistTurn
import com.gekkotron.nidoo.assist.QuestionKind
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.voice.ErrorKind
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class VoiceSheetDispatchTest {
    @get:Rule val rule = createComposeRule()

    private fun turn(outcome: AssistOutcome, reply: String, question: AssistQuestion? = null, id: String = "t1") =
        AssistTurn(id, "allume", outcome, reply, question)

    private val choice = AssistQuestion("p1", QuestionKind.CHOICE, listOf("Chambre Léa", "Chambre parentale"))
    private val confirm = AssistQuestion("p2", QuestionKind.CONFIRM, emptyList())

    @Test fun `a choice question shows one row per choice and taps send the index`() {
        var chosen = -1
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choice))),
                    onChoose = { chosen = it },
                )
            }
        }
        rule.onNodeWithText("Dans quelle pièce ?").assertExists()
        rule.onNodeWithText("Chambre Léa").assertExists()
        rule.onNodeWithText("Répondre").assertExists()
        rule.onNodeWithText("Chambre parentale").performClick()
        assertEquals(1, chosen)
    }

    @Test fun `a confirm question shows Yes and No`() {
        var answer: Boolean? = null
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.CONFIRMING, "Désarmer l'alarme ?", confirm))),
                    onConfirm = { answer = it },
                )
            }
        }
        rule.onNodeWithText("Non").assertExists()
        rule.onNodeWithText("Oui").performClick()
        assertEquals(true, answer)
    }

    @Test fun `an expired question shows Try again and no choices`() {
        var again = false
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choice), expired = true)),
                    onListenAgain = { again = true },
                )
            }
        }
        rule.onNodeWithText("La question a expiré.").assertExists()
        rule.onNodeWithText("Chambre Léa").assertDoesNotExist()
        rule.onNodeWithText("Répondre").assertDoesNotExist()
        rule.onNodeWithText("Réessayer").performClick()
        assertTrue(again)
    }

    @Test fun `not understood shows Try again`() {
        var again = false
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.NOT_UNDERSTOOD, "Je n'ai pas compris."))),
                    onListenAgain = { again = true },
                )
            }
        }
        rule.onNodeWithText("Je n'ai pas compris.").assertExists()
        rule.onNodeWithText("Réessayer").performClick()
        assertTrue(again)
    }

    @Test fun `an acted turn shows only its reply`() {
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ACTED, "C'est fait."))))
            }
        }
        rule.onNodeWithText("C'est fait.").assertExists()
        rule.onNodeWithText("Réessayer").assertDoesNotExist()
        rule.onNodeWithText("Répondre").assertDoesNotExist()
    }

    @Test fun `repeat shows only when canRepeat`() {
        val canRepeat = MutableStateFlow(false)
        var repeated = false
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ANSWERED, "Il fait 21 degrés."))),
                    canRepeat = canRepeat,
                    onRepeat = { repeated = true },
                )
            }
        }
        rule.onNodeWithText("Répéter").assertDoesNotExist()
        canRepeat.value = true
        rule.waitForIdle()
        rule.onNodeWithText("Répéter").performClick()
        assertTrue(repeated)
    }

    @Test fun `each error kind shows its text and the right action`() {
        val texts = mapOf(
            ErrorKind.NO_HUB to "L'assistant a besoin du hub.",
            ErrorKind.HUB_UNREACHABLE to "Hub injoignable.",
            ErrorKind.GATEWAY_REFUSED to "La passerelle n'autorise pas le hub : ajoutez son adresse aux règles de la clé.",
            ErrorKind.HUB_FAILED to "Le hub n'a pas pu répondre.",
            ErrorKind.SPEECH to "Reconnaissance vocale indisponible",
            ErrorKind.PERMISSION to "Permission micro requise",
            ErrorKind.SILENCE to "Je n'ai rien entendu",
            ErrorKind.DISABLED to "L'assistant vocal est désactivé. Activez-le dans les Réglages.",
        )
        val state = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Error(ErrorKind.NO_HUB))
        rule.setContent { NidooTheme(ThemeChoice.IVOIRE) { sheet(state) } }
        for ((kind, text) in texts) {
            state.value = VoiceSessionState.Error(kind)
            rule.waitForIdle()
            rule.onNodeWithText(text).assertExists()
            if (kind == ErrorKind.DISABLED) {
                rule.onNodeWithText("Ouvrir les Réglages").assertExists()
                rule.onNodeWithText("Réessayer").assertDoesNotExist()
            } else if (kind == ErrorKind.NO_HUB || kind == ErrorKind.GATEWAY_REFUSED) {
                rule.onNodeWithText("Réessayer").assertDoesNotExist()
                rule.onNodeWithText("Ouvrir les Réglages").assertDoesNotExist()
            } else {
                rule.onNodeWithText("Réessayer").assertExists()
                rule.onNodeWithText("Ouvrir les Réglages").assertDoesNotExist()
            }
        }
    }

    @Test fun `closeRequested closes unless the sheet was touched`() {
        val closeRequested = MutableStateFlow(false)
        var closed = 0
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ACTED, "C'est fait."))),
                    closeRequested = closeRequested,
                    onCancel = { closed++ },
                )
            }
        }
        closeRequested.value = true
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    @Test fun `a touched sheet does not close by itself`() {
        val closeRequested = MutableStateFlow(false)
        var closed = 0
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                sheet(
                    MutableStateFlow(VoiceSessionState.Turn(turn(AssistOutcome.ACTED, "C'est fait."))),
                    closeRequested = closeRequested,
                    onCancel = { closed++ },
                )
            }
        }
        rule.onNodeWithText("C'est fait.").performClick()
        val afterTouch = closed
        closeRequested.value = true
        rule.waitForIdle()
        assertFalse(closed > afterTouch)
    }

    @Test fun `the tap that answered a question does not keep the next turn open`() {
        val state = MutableStateFlow<VoiceSessionState>(
            VoiceSessionState.Turn(turn(AssistOutcome.ASKED, "Dans quelle pièce ?", choice, id = "t1")),
        )
        val closeRequested = MutableStateFlow(false)
        var closed = 0
        rule.setContent {
            NidooTheme(ThemeChoice.IVOIRE) { sheet(state, closeRequested = closeRequested, onCancel = { closed++ }) }
        }
        rule.onNodeWithText("Chambre Léa").performClick()
        state.value = VoiceSessionState.Sending(null)
        rule.waitForIdle()
        state.value = VoiceSessionState.Turn(turn(AssistOutcome.ACTED, "Chambre Léa éteinte.", id = "t2"))
        rule.waitForIdle()
        closeRequested.value = true
        rule.waitForIdle()
        assertEquals(1, closed)
    }
}
