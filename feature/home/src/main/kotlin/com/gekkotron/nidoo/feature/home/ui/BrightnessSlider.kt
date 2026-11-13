package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.gekkotron.nidoo.feature.home.R
import com.gekkotron.nidoo.feature.home.presentation.CardFormatter
import kotlin.math.roundToInt

/**
 * A 1..255 brightness slider under its label, with the value shown as a percent beside the label and read
 * to TalkBack as the slider's state (it would otherwise announce the raw 1..255 position).
 */
@Composable
internal fun BrightnessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.sheet_brightness),
    enabled: Boolean = true,
) {
    val percent = stringResource(R.string.value_percent, CardFormatter.percent(value.roundToInt()))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(percent, Modifier.testTag("brightness-value"))
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 1f..255f,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.semantics {
            contentDescription = label
            stateDescription = percent
        },
    )
}
