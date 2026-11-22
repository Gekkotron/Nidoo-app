package com.gekkotron.nidoo.designsystem.components

import com.gekkotron.nidoo.designsystem.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.Route
import com.gekkotron.nidoo.transport.TransportMode

/** Link status and the route choice when the profile has a gateway; everything else lives in Settings (settings spec §5.3). Opened from the [LinkPill] on every tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(
    link: LinkState,
    route: Route?,
    onRouteChange: (Route) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(linkDescription(link), style = NidooTheme.type.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (route != null) RoutePicker(route, onRouteChange)
            TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.link_settings)) }
        }
    }
}

@Composable
private fun linkDescription(link: LinkState): String = when (link) {
    LinkState.Connecting -> stringResource(Res.string.link_connecting)
    is LinkState.Connected -> stringResource(
        when (link.mode) {
            TransportMode.DIRECT -> Res.string.link_direct
            TransportMode.GATEWAY -> Res.string.link_gateway
            TransportMode.DEMO -> Res.string.link_demo
        },
    )
    is LinkState.Reconnecting -> stringResource(Res.string.link_reconnecting, link.attempt)
    is LinkState.Offline -> stringResource(
        when (link.reason) {
            LinkState.Offline.REJECTED -> Res.string.link_rejected
            LinkState.Offline.CLOSED -> Res.string.link_offline_closed
            LinkState.Offline.PAUSED -> Res.string.link_offline_paused
            LinkState.Offline.NOT_CONFIGURED -> Res.string.link_offline_not_configured
            LinkState.Offline.NOT_STARTED -> Res.string.link_offline_not_started
            LinkState.Offline.FAILED -> Res.string.link_offline_failed
            LinkState.Offline.KEY_REJECTED -> Res.string.link_key_rejected
            LinkState.Offline.CLOCK_SKEW -> Res.string.link_clock_skew
            LinkState.Offline.GATEWAY_REFUSED -> Res.string.link_gateway_refused
            // The reason constants are English; an unknown one is never shown raw.
            else -> Res.string.link_offline
        },
    )
}
