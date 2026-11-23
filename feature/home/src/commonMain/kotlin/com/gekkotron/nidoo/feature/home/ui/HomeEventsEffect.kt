package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.gekkotron.nidoo.feature.home.resources.*
import com.gekkotron.nidoo.feature.home.presentation.HomeEvent
import com.gekkotron.nidoo.feature.home.presentation.RoomKind
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Kind
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Spec §4.4 / §7: "Not confirmed — Retry" and refused commands, as snackbars. */
@Composable
fun HomeEventsEffect(
    events: Flow<HomeEvent>,
    snackbar: SnackbarHostState,
    onRetry: (String, Command) -> Unit,
    onRetryGroup: (String, Command, List<String>) -> Unit = { _, _, _ -> },
    onAsk: (HomeEvent.AskBeforeAction) -> Unit = {},
    onRetryRoom: (HomeEvent.RoomNotConfirmed) -> Unit = {},
    /** The room's display name for an area id, for the whole-room snackbar. */
    roomName: (String) -> String = { it },
) {
    val retry by rememberUpdatedState(onRetry)
    val retryGroup by rememberUpdatedState(onRetryGroup)
    val ask by rememberUpdatedState(onAsk)
    val retryRoom by rememberUpdatedState(onRetryRoom)
    val nameOf by rememberUpdatedState(roomName)
    LaunchedEffect(events, snackbar) {
        events.collect { event ->
            when (event) {
                is HomeEvent.AskBeforeAction -> ask(event)
                is HomeEvent.NotConfirmed -> {
                    val result = snackbar.showSnackbar(
                        message = getString(Res.string.home_not_confirmed),
                        actionLabel = getString(Res.string.home_retry),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) retry(event.id, event.command)
                }
                is HomeEvent.Rejected -> snackbar.showSnackbar(getString(Res.string.home_rejected, event.reason))
                HomeEvent.SaveFailed -> snackbar.showSnackbar(getString(Res.string.home_save_failed))
                HomeEvent.WaitingForConnection -> snackbar.showSnackbar(getString(Res.string.home_waiting_connection))
                is HomeEvent.RoomNotConfirmed -> {
                    val count = event.failed.size
                    val message = if (count == 1) {
                        getString(Res.string.home_group_not_confirmed_one, event.failedNames.single())
                    } else {
                        val plural = if (event.kind == RoomKind.COVER) Res.plurals.home_room_not_confirmed_covers else Res.plurals.home_room_not_confirmed_lights
                        getPluralString(plural, count, count, nameOf(event.areaId))
                    }
                    val result = snackbar.showSnackbar(message = message, actionLabel = getString(Res.string.home_retry), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) retryRoom(event)
                }
                is HomeEvent.GroupNotConfirmed -> {
                    val message = if (event.failedIds.size == 1) {
                        getString(Res.string.home_group_not_confirmed_one, event.failedNames.single())
                    } else {
                        val plural = if (event.kind == Kind.COVER) Res.plurals.home_group_not_confirmed_covers else Res.plurals.home_group_not_confirmed_lights
                        getPluralString(plural, event.failedIds.size, event.failedIds.size)
                    }
                    val result = snackbar.showSnackbar(message = message, actionLabel = getString(Res.string.home_retry), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) retryGroup(event.groupId, event.command, event.failedIds)
                }
            }
        }
    }
}
