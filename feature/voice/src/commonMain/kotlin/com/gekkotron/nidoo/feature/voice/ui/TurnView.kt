package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import com.gekkotron.nidoo.assist.AssistOutcome
import com.gekkotron.nidoo.assist.AssistTurn
import com.gekkotron.nidoo.assist.QuestionKind
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.voice.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.gekkotron.nidoo.feature.voice.resources.voice_answer_by_voice
import com.gekkotron.nidoo.feature.voice.resources.voice_no
import com.gekkotron.nidoo.feature.voice.resources.voice_question_expired
import com.gekkotron.nidoo.feature.voice.resources.voice_repeat
import com.gekkotron.nidoo.feature.voice.resources.voice_retry
import com.gekkotron.nidoo.feature.voice.resources.voice_yes

/** The hub's turn: its reply, then the buttons its question or outcome calls for. */
@Composable
internal fun ColumnScope.TurnView(
    turn: AssistTurn,
    expired: Boolean,
    canRepeat: Boolean,
    onChoose: (Int) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onListenAgain: () -> Unit,
    onRepeat: () -> Unit,
) {
    val c = NidooTheme.colors
    Text(turn.reply, style = NidooTheme.type.body, color = c.content, textAlign = TextAlign.Center)
    val question = turn.question
    when {
        question != null && expired -> {
            Text(stringResource(Res.string.voice_question_expired), style = NidooTheme.type.body, color = c.content, textAlign = TextAlign.Center)
            VoiceRow(stringResource(Res.string.voice_retry), onListenAgain)
        }
        question != null -> {
            when (question.kind) {
                QuestionKind.CHOICE -> question.choices.forEachIndexed { i, choice -> VoiceRow(choice, { onChoose(i) }) }
                QuestionKind.CONFIRM -> {
                    VoiceRow(stringResource(Res.string.voice_yes), { onConfirm(true) })
                    VoiceRow(stringResource(Res.string.voice_no), { onConfirm(false) })
                }
            }
            VoiceRow(stringResource(Res.string.voice_answer_by_voice), onListenAgain)
        }
        turn.outcome == AssistOutcome.NOT_UNDERSTOOD || turn.outcome == AssistOutcome.FAILED ->
            VoiceRow(stringResource(Res.string.voice_retry), onListenAgain)
    }
    if (canRepeat) VoiceRow(stringResource(Res.string.voice_repeat), onRepeat)
}
