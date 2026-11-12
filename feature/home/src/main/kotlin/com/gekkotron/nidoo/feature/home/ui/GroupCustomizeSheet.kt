package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardUi

/** A hub group's name and favorite, kept on the group entity's own override. [onSave] gets the typed name and the favorite flag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupCustomizeSheet(card: CardUi, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(card.id) { mutableStateOf(card.name) }
    var favorite by rememberSaveable(card.id) { mutableStateOf(card.ui.favorite) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TitleWithFavorite(stringResource(R.string.customize_group_title), favorite) { favorite = it }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.customize_group_name)) },
                placeholder = { Text(groupName(card.copy(name = ""))) },
                supportingText = { Text(stringResource(R.string.customize_group_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(name, favorite); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = NidooDimens.minTouch),
            ) { Text(stringResource(R.string.customize_save)) }
        }
    }
}
