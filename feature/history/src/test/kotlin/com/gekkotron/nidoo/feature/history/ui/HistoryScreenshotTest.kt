package com.gekkotron.nidoo.feature.history.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.history.distinctColors
import com.gekkotron.nidoo.feature.history.luminanceAt
import com.gekkotron.nidoo.feature.history.presentation.ChartContent
import com.gekkotron.nidoo.feature.history.presentation.ChartMode
import com.gekkotron.nidoo.feature.history.presentation.HistoryUi
import com.gekkotron.nidoo.feature.history.renderPng
import com.gekkotron.nidoo.history.DailyTotals
import com.gekkotron.nidoo.history.DayTotal
import com.gekkotron.nidoo.history.HistoryPoint
import com.gekkotron.nidoo.history.HistoryRange
import com.gekkotron.nidoo.history.Series
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val start = 1_790_000_000L

    /** A house's power over a day: a fridge cycling on a low base, then breakfast, lunch and evening peaks. */
    private fun housePower(i: Int): Double {
        val hour = ((start + i * 600L) % 86_400L) / 3600.0
        fun peak(at: Double, watts: Double, width: Double) = watts * exp(-((hour - at) / width).pow(2))
        val fridge = if (i % 6 < 2) 90.0 else 0.0
        val jitter = ((i * 37) % 11 - 5) * 8.0
        return 190.0 + fridge + jitter + peak(7.3, 1500.0, 0.6) + peak(12.6, 750.0, 0.5) + peak(19.6, 2100.0, 1.0)
    }

    private fun line(): ChartContent.Line {
        val points = (0 until 150).map {
            val mean = housePower(it)
            HistoryPoint(start + it * 600L, mean, mean - 30, mean + 50)
        }
        return ChartContent.Line(
            Series(600, points),
            min = points.minOf { it.min }, avg = points.map { it.mean }.average(), max = points.maxOf { it.max },
        )
    }

    private fun bars() = ChartContent.Bars(
        DailyTotals((0 until 7).map { DayTotal(start + it * 86_400L, 6.0 + (it * 7 % 5)) }),
        total = 52.0,
    )

    private fun render(theme: ThemeChoice, name: String, state: HistoryUi) = run {
        compose.setContent {
            NidooTheme(theme) {
                NidooBackground {
                    HistoryScreen(
                        state, onBack = {}, onRange = {}, onMode = {}, onRetry = {},
                        zone = ZoneOffset.UTC, locale = Locale.UK,
                    )
                }
            }
        }
        compose.renderPng(name)
    }

    @Test
    fun `a line in Night Glass`() {
        val state = HistoryUi(title = "House power", room = "Whole home", live = 240.0, unit = "W", energy = true, toggle = true, content = line())
        val bitmap = render(ThemeChoice.NIGHT_GLASS, "history-line-night", state)
        assertTrue(bitmap.distinctColors() > 8)
        assertTrue(bitmap.luminanceAt(4, 4) < 0.35f)
    }

    @Test
    fun `a line in Ivoire`() {
        val state = HistoryUi(title = "House power", room = "Whole home", live = 240.0, unit = "W", range = HistoryRange.D7, content = line())
        val bitmap = render(ThemeChoice.IVOIRE, "history-line-warm", state)
        assertTrue(bitmap.distinctColors() > 8)
        assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }

    @Test
    fun `daily bars in Night Glass`() {
        val state = HistoryUi(live = 1800.0, unit = "W", range = HistoryRange.D7, energy = true, toggle = true, mode = ChartMode.DAILY, content = bars())
        val bitmap = render(ThemeChoice.NIGHT_GLASS, "history-bars-night", state)
        assertTrue(bitmap.distinctColors() > 8)
        assertTrue(bitmap.luminanceAt(4, 4) < 0.35f)
    }

    @Test
    fun `not recorded in Ivoire`() {
        val state = HistoryUi(title = "Lamp", content = ChartContent.NotRecorded)
        val bitmap = render(ThemeChoice.IVOIRE, "history-not-recorded-warm", state)
        assertTrue(bitmap.distinctColors() > 4)
        assertTrue(bitmap.luminanceAt(4, 4) > 0.6f)
    }
}
