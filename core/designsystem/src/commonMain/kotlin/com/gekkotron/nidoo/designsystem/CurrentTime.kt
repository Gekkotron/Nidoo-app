package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

/** The current time, refreshed every minute so "2 min" does not freeze on a screen left open. */
@Composable
fun rememberCurrentTime(): State<Instant> = produceState(Clock.System.now()) {
    while (true) {
        delay(60_000)
        value = Clock.System.now()
    }
}
