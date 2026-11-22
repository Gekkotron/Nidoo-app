package com.gekkotron.nidoo.designsystem.icons

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LoopPhaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a new fan speed changes the pace at once, without restarting the loop`() {
        compose.mainClock.autoAdvance = false
        var period by mutableIntStateOf(4000)
        lateinit var phase: State<Float>
        compose.setContent { phase = rememberLoopPhase(period) }
        compose.mainClock.advanceTimeBy(1000)
        val before = phase.value
        assertEquals(0.25f, before, 0.03f)

        period = 1000
        compose.mainClock.advanceTimeBy(200)
        // 200 ms at the new 1 s period: a fifth of a loop further on, from where it was.
        assertEquals(before + 0.2f, phase.value, 0.04f)
    }
}
