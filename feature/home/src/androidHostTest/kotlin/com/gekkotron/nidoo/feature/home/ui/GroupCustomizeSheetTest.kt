package com.gekkotron.nidoo.feature.home.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.home.hubGroupCard
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Kind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class GroupCustomizeSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val group = hubGroupCard(
        testEntity("bay", Kind.COVER, "Bay window", area = "living"),
        testEntity("side", Kind.COVER, "Side window", area = "living"),
        kind = Kind.COVER,
    )

    private var saved: Pair<String, Boolean>? = null

    private fun show() = compose.setContent {
        NidooTheme(ThemeChoice.NIGHT_GLASS) { GroupCustomizeSheet(group, onSave = { n, f -> saved = n to f }, onDismiss = {}) }
    }

    @Test
    fun `saving reports the typed name`() {
        show()
        compose.onNode(hasSetTextAction()).performTextReplacement("Baie")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals("Baie" to false, saved)
    }

    @Test
    fun `toggling Favorite and saving reports the group as favorited`() {
        show()
        compose.onNodeWithContentDescription("Favourite").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(group.name to true, saved)
    }
}
