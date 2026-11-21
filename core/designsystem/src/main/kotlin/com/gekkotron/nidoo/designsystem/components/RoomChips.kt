package com.gekkotron.nidoo.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme

data class ChipItem(val key: String, val label: String)

@Composable
fun RoomChips(items: List<ChipItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = NidooTheme.colors
    // The selected pill is brought into view: with many rooms it may start off-screen (rooms design §6.4).
    val listState = rememberLazyListState()
    LaunchedEffect(selectedKey, items.size) {
        val index = items.indexOfFirst { it.key == selectedKey }
        if (index < 0) return@LaunchedEffect
        // Wait for the first layout: before it nothing is "visible" and the row would always jump,
        // pushing the leading pill ("‹ Rooms") off the edge.
        val info = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fits = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!fits) listState.animateScrollToItem(index)
    }
    LazyRow(modifier, state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.key }) { item ->
            val selected = item.key == selectedKey
            Box(
                Modifier
                    .heightIn(min = NidooDimens.minTouch)
                    // No ripple: it filled the rectangular touch area, not the pill (the pill's colour shows the selection).
                    .selectable(selected = selected, interactionSource = null, indication = null, role = Role.Tab) { onSelect(item.key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    style = NidooTheme.type.label,
                    color = if (selected) c.chipSelectedContent else c.content,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) c.chipSelected else c.chip)
                        .border(1.dp, if (selected) c.chipSelected else c.chipBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
