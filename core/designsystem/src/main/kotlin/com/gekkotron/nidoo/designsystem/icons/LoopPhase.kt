package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * A 0..1 phase looping every [periodMs], for icons that read it only in their draw lambda. The phase is
 * accumulated frame by frame, so a new period (a new fan speed) changes the pace at once and carries on
 * from where the loop was, instead of being ignored (an infinite transition keeps its first spec) or
 * restarting it. Frames are infinite-animation frames, so tests and idling treat it like an infinite transition.
 */
@Composable
internal fun rememberLoopPhase(periodMs: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val period by rememberUpdatedState(periodMs.coerceAtLeast(1))
    LaunchedEffect(Unit) {
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val elapsedMs = (now - last) / 1_000_000f
                last = now
                phase.floatValue = (phase.floatValue + elapsedMs / period) % 1f
            }
        }
    }
    return phase
}
