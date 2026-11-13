package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A spinning icon must animate in the draw phase: recomposing every frame costs a tile grid its frame budget. */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
class KindIconRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    private val composed = mutableListOf<String>()

    /** Records every composition of a function whose trace info names [function]. */
    private fun trace(function: String) = Composer.setTracer(object : CompositionTracer {
        override fun isTraceInProgress() = true
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            if (info.startsWith("$function ") || info.contains(".$function ")) composed += info
        }
        override fun traceEventEnd() = Unit
    })

    @After
    fun stopTracing() = Composer.setTracer(null)

    @Test
    fun `the running AC fan spins without recomposing every frame`() {
        trace("ClimateIcon")
        compose.mainClock.autoAdvance = false
        compose.setContent { NidooTheme(ThemeChoice.NIGHT_GLASS) { KindIcon(IconSpec.Climate(running = true, fanMode = "high")) } }
        compose.mainClock.advanceTimeByFrame()
        val settled = composed.size
        assertTrue(settled > 0, "the tracer saw no ClimateIcon: the test would pass vacuously")
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(settled, composed.size, "ClimateIcon recomposed ${composed.size - settled} times in 30 frames")
    }
}
