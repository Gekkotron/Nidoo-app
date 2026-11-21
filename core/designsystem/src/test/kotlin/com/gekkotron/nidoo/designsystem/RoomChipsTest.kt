package com.gekkotron.nidoo.designsystem

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.components.ChipItem
import com.gekkotron.nidoo.designsystem.components.RoomChips
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RoomChipsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `selected chip is marked and a click reports its key`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            NidooTheme(ThemeChoice.IVOIRE) {
                RoomChips(listOf(ChipItem("all", "All"), ChipItem("area:kitchen", "Kitchen")), "all", { picked += it })
            }
        }
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Kitchen").assertIsNotSelected().performClick()
        assertEquals(listOf("area:kitchen"), picked)
    }

    private val many = listOf(ChipItem("rooms", "‹ Rooms")) + (1..12).map { ChipItem("area:$it", "Room number $it") }

    @Test
    fun `a selected chip that already fits leaves the row where it is`() {
        compose.setContent { NidooTheme(ThemeChoice.IVOIRE) { RoomChips(many, "area:1", {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("‹ Rooms").assertIsDisplayed()
        compose.onNodeWithText("Room number 1").assertIsDisplayed()
    }

    @Test
    fun `a selected chip that starts off-screen is brought into view`() {
        compose.setContent { NidooTheme(ThemeChoice.IVOIRE) { RoomChips(many, "area:12", {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Room number 12").assertIsDisplayed()
    }
}
