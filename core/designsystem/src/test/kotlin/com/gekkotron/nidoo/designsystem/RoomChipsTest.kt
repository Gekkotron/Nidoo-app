package com.gekkotron.nidoo.designsystem

import androidx.compose.ui.test.assertIsNotSelected
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
}
