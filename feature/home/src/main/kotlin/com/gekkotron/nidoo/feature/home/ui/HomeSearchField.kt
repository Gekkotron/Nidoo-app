package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.feature.home.R

/**
 * Home's search field (rooms design §6.5). Always on screen on Rooms, in a room and on All devices,
 * where it must not grab the keyboard by itself; [autoFocus] is for the header's magnifier, which
 * opens it on the other views. The caller owns [text] (see HomeScreen).
 */
@Composable
internal fun HomeSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    showClear: Boolean,
    autoFocus: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { StaticIcon(NidooGlyph.SEARCH, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (showClear) {
            {
                val closeLabel = stringResource(R.string.search_close)
                IconButton(onClick = onClear, modifier = Modifier.semantics { contentDescription = closeLabel }) {
                    StaticIcon(NidooGlyph.CLOSE, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { onFocusChange(it.isFocused) },
    )
}
