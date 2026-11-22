package com.gekkotron.nidoo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.components.LinkPill
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import org.koin.compose.viewmodel.koinViewModel

/** Placeholder root of the shared app until the real screens move to commonMain: the Nidoo theme and the demo house. */
@Composable
fun NidooRoot(demo: DemoHouseViewModel = koinViewModel()) = NidooTheme {
    val summary by demo.summary.collectAsState()
    NidooBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nidoo", style = NidooTheme.type.display, color = NidooTheme.colors.content)
            LinkPill(LinkState.Connected(TransportMode.DEMO), onClick = null)
            Text(sharedCoreSummary(), style = NidooTheme.type.body, color = NidooTheme.colors.contentMuted)
            Text(summary ?: "…", style = NidooTheme.type.body, color = NidooTheme.colors.contentMuted)
        }
    }
}
