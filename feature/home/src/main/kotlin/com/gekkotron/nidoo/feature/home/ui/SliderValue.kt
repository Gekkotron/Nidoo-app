package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A device slider's shown value. It follows the device's reported state, except while the user drags:
 * a state echo landing mid-drag must not yank the thumb back (the hue wheel guards its marker the same way).
 * Wire [drag] to `onValueChange` and [release] to `onValueChangeFinished`.
 */
@Stable
internal class SliderValue(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    private var dragging = false

    fun drag(to: Float) {
        dragging = true
        value = to
    }

    /** Ends the drag and returns the value to send. */
    fun release(): Float {
        dragging = false
        return value
    }

    fun follow(upstream: Float) {
        if (!dragging) value = upstream
    }
}

@Composable
internal fun rememberSliderValue(upstream: Float): SliderValue {
    val slider = remember { SliderValue(upstream) }
    LaunchedEffect(upstream) { slider.follow(upstream) }
    return slider
}
