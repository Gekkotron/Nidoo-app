package com.gekkotron.nidoo.designsystem.components

import com.gekkotron.nidoo.designsystem.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode

/** The broker connection status; [onClick] null shows it as a plain status (the Energy header). */
@Composable
fun LinkPill(link: LinkState, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (label, tone) = when (link) {
        LinkState.Connecting -> Res.string.pill_connecting to PillTone.WARNING
        is LinkState.Connected -> when (link.mode) {
            TransportMode.DIRECT -> Res.string.pill_direct
            TransportMode.GATEWAY -> Res.string.pill_gateway
            TransportMode.DEMO -> Res.string.pill_demo
        } to PillTone.CONNECTED
        is LinkState.Reconnecting -> Res.string.pill_reconnecting to PillTone.WARNING
        is LinkState.Offline -> Res.string.pill_offline to PillTone.OFFLINE
    }
    val clickLabel = stringResource(Res.string.link_details)
    StatusPill(
        stringResource(label),
        tone,
        if (onClick == null) {
            modifier
        } else {
            modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
        },
    )
}
