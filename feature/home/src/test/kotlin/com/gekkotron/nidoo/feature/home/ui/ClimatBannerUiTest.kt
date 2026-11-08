package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.presentation.ClimatBannerUi
import com.gekkotron.nidoo.feature.home.presentation.ClimatBoostUi
import com.gekkotron.nidoo.feature.home.presentation.ClimatWhy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class ClimatBannerUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1_000

    private val ui = ClimatBannerUi(
        zone = "living", reading = 19.4, target = 20.0, why = ClimatWhy.SCHEDULE, reason = "21 °C from 18:00",
        boost = null, holdUntil = null, canBoost = true, min = 16.0, max = 30.0,
    )

    private var boosts = 0
    private var cancels = 0
    private val starts = mutableListOf<Pair<Double, Int>>()

    private fun banner(ui: ClimatBannerUi) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) { ClimatBanner(ui, onBoost = { boosts++ }, onCancel = { cancels++ }) }
    }

    private fun sheet(ui: ClimatBannerUi, sending: Boolean = false, error: Boolean = false) = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) {
            BoostSheet(ui, sending = sending, error = error, onStart = { t, m -> starts += t to m }, onCancel = { cancels++ }, onDismiss = {})
        }
    }

    @Test
    fun bannerShowsReadingTargetAndBoost() {
        banner(ui)
        compose.onNodeWithText("19.4°").assertExists()
        compose.onNodeWithText("20°").assertExists()
        compose.onNodeWithText("Schedule · 21 °C from 18:00").assertExists()
        compose.onNodeWithText("Boost").performClick()
        assertEquals(1, boosts)
    }

    @Test
    fun bannerWithoutNumbersShowsDashes() {
        banner(ui.copy(reading = null, target = null, why = ClimatWhy.OTHER))
        compose.onNodeWithText("—°").assertExists()
    }

    @Test
    fun bannerWithBoostShowsUntilAndCancel() {
        banner(ui.copy(why = ClimatWhy.BOOST, boost = ClimatBoostUi(22.0, now + 3600)))
        compose.onNodeWithText("Boost 22° until", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancels)
        // While boosted, tapping the banner reopens the sheet to update it.
        compose.onNodeWithText("Boost 22° until", substring = true).performClick()
        assertEquals(1, boosts)
    }

    @Test
    fun bannerWithHoldShowsHoldAndCancel() {
        banner(ui.copy(why = ClimatWhy.HOLD, holdUntil = now + 600))
        compose.onNodeWithText("Hold until", substring = true).assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun bannerSafetyHasNoBoostButton() {
        banner(ui.copy(why = ClimatWhy.SAFETY, canBoost = false))
        compose.onNodeWithText("Safety").assertExists()
        compose.onNodeWithText("Boost").assertDoesNotExist()
    }

    @Test
    fun sheetDefaultsToOneHour() {
        sheet(ui)
        compose.onNodeWithText("1 h").assertIsSelected()
        compose.onNodeWithText("Start boost").performClick()
        assertEquals(listOf(20.0 to 60), starts)
    }

    @Test
    fun sheetStartSendsChosenValues() {
        sheet(ui)
        compose.onNodeWithContentDescription("Increase").performClick()
        compose.onNodeWithText("1 h 30").performClick()
        compose.onNodeWithText("Start boost").performClick()
        assertEquals(listOf(20.5 to 90), starts)
    }

    @Test
    fun sheetChipSetsTarget() {
        sheet(ui)
        compose.onNodeWithText("22°").performClick()
        compose.onNodeWithText("2 h").performClick()
        compose.onNodeWithText("Start boost").performClick()
        assertEquals(listOf(22.0 to 120), starts)
    }

    @Test
    fun sheetClampsToMax() {
        sheet(ui.copy(target = 29.5, max = 30.0))
        repeat(3) { compose.onNodeWithContentDescription("Increase").performClick() }
        compose.onNodeWithContentDescription("Increase").assertIsNotEnabled()
        compose.onNodeWithText("Start boost").performClick()
        assertEquals(listOf(30.0 to 60), starts)
    }

    @Test
    fun sheetWithLiveBoostUpdatesAndCancels() {
        sheet(ui.copy(why = ClimatWhy.BOOST, boost = ClimatBoostUi(23.0, now + 85 * 60)))
        compose.onNodeWithText("1 h 30").assertIsSelected()
        compose.onNodeWithText("Update").performClick()
        assertEquals(listOf(23.0 to 90), starts)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun sheetDisabledWhileSending() {
        sheet(ui, sending = true)
        compose.onNodeWithText("Start boost").assertIsNotEnabled()
    }

    @Test
    fun sheetShowsTheError() {
        sheet(ui, error = true)
        compose.onNodeWithText("Climat didn't take the boost").assertExists()
    }
}
