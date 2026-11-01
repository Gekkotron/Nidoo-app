package com.gekkotron.nidoo.feature.widget.code

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.alarm.CodeKeypadSheet
import com.gekkotron.nidoo.feature.widget.WidgetComponentProvider
import com.gekkotron.nidoo.feature.widget.model.WidgetCommand
import com.gekkotron.nidoo.model.CodeFormat

/**
 * A transparent host for the alarm code keypad (widgets spec §2.3). The code lives only in the sheet's state and the
 * command work's input; it is never put in an intent extra or a store (spec §4).
 */
class WidgetCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        val command = intent.getStringExtra(EXTRA_COMMAND)?.let(WidgetCommand::decode)
        val format = intent.getStringExtra(EXTRA_FORMAT)?.let { name -> CodeFormat.entries.firstOrNull { it.name == name } } ?: CodeFormat.NUMBER
        if (id == null || command == null) {
            finish()
            return
        }
        val scheduler = (application as WidgetComponentProvider).widgetComponent().scheduler
        setContent {
            NidooTheme {
                CodeKeypadSheet(
                    format = format,
                    onSubmit = { code ->
                        scheduler.send(id, command, code)
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_FORMAT = "format"

        fun intent(context: Context, id: String, command: WidgetCommand, format: CodeFormat): Intent =
            Intent(context, WidgetCodeActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_COMMAND, command.encode())
                .putExtra(EXTRA_FORMAT, format.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
