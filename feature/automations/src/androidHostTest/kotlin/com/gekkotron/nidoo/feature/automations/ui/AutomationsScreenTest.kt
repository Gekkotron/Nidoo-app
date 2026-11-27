package com.gekkotron.nidoo.feature.automations.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooBackground
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.automations.presentation.ALL_KEY
import com.gekkotron.nidoo.feature.automations.presentation.AlarmEditor
import com.gekkotron.nidoo.feature.automations.presentation.AlarmRow
import com.gekkotron.nidoo.feature.automations.presentation.AreaLabel
import com.gekkotron.nidoo.feature.automations.presentation.AutomationChip
import com.gekkotron.nidoo.feature.automations.presentation.AutomationRow
import com.gekkotron.nidoo.feature.automations.presentation.AutomationsUiState
import com.gekkotron.nidoo.feature.automations.presentation.RoutineSection
import com.gekkotron.nidoo.feature.automations.resources.Res
import com.gekkotron.nidoo.feature.automations.resources.automations_chip_all
import com.gekkotron.nidoo.feature.automations.resources.automations_chip_unassigned
import com.gekkotron.nidoo.feature.automations.resources.automations_empty_body
import com.gekkotron.nidoo.feature.automations.resources.automations_empty_title
import com.gekkotron.nidoo.feature.automations.resources.automations_loading
import com.gekkotron.nidoo.feature.automations.resources.automations_offline_banner
import com.gekkotron.nidoo.feature.automations.resources.automations_title
import com.gekkotron.nidoo.feature.automations.resources.automations_toggle_failed
import com.gekkotron.nidoo.feature.automations.resources.wake_up_empty_body
import com.gekkotron.nidoo.feature.automations.resources.wake_up_empty_title
import com.gekkotron.nidoo.feature.automations.resources.wake_up_pick_a_day
import com.gekkotron.nidoo.feature.automations.resources.wake_up_save_failed
import com.gekkotron.nidoo.feature.automations.resources.wake_up_unreadable
import com.gekkotron.nidoo.flows.WakeUpTime
import java.io.File
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h914dp-xxhdpi")
class AutomationsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<String>()
    private val toggled = mutableListOf<Pair<String, Boolean>>()
    private var toastShown = 0
    private val sections = mutableListOf<RoutineSection>()
    private val tapped = mutableListOf<String>()
    private val dayToggles = mutableListOf<Int>()
    private var saves = 0

    private val chips = listOf(AutomationChip.All, AutomationChip.Room("living", "Living room"), AutomationChip.Unassigned)

    private val rows = listOf(
        AutomationRow("a", "Evening lights", AreaLabel.Named("Living room"), enabled = true, toggleEnabled = true),
        AutomationRow("b", "Close shutters", AreaLabel.Unassigned, enabled = false, toggleEnabled = true),
        AutomationRow("c", "Vacation mode", null, enabled = false, toggleEnabled = true),
    )

    private fun state(
        isLoading: Boolean = false,
        isOffline: Boolean = false,
        rows: List<AutomationRow> = this.rows,
        toast: StringResource? = null,
        section: RoutineSection = RoutineSection.Flows,
        alarms: List<AlarmRow> = emptyList(),
        editor: AlarmEditor? = null,
    ) = AutomationsUiState(isLoading, isOffline, chips, ALL_KEY, rows, toast, section, alarms, editor)

    private fun show(state: AutomationsUiState, dark: Boolean = true) = compose.setContent {
        NidooTheme(if (dark) ThemeChoice.NIGHT_GLASS else ThemeChoice.IVOIRE) {
            NidooBackground {
                AutomationsScreen(
                    state,
                    onChipSelected = { selected += it },
                    onToggle = { id, on -> toggled += id to on },
                    onToastShown = { toastShown++ },
                    onSectionSelected = { sections += it },
                    onAlarmTapped = { tapped += it },
                    onEditorTime = { _, _ -> },
                    onEditorDayToggled = { dayToggles += it },
                    onEditorDismissed = {},
                    onEditorSave = { saves++ },
                )
            }
        }
    }

    private fun text(res: StringResource) = runBlocking { getString(res) }

    private val weekdays = WakeUpTime(7, 5, listOf(1, 2, 3, 4, 5))
    private val alarms = listOf(
        AlarmRow("w1", "Weekdays", weekdays, enabled = true, toggleEnabled = true),
        AlarmRow("w2", "Broken", null, enabled = false, toggleEnabled = true),
    )

    @Test
    fun `the section switch shows both sections and reports the choice`() {
        show(state())
        compose.onNodeWithTag("routines_section_flows").assertExists()
        compose.onNodeWithTag("routines_section_wake_up").performClick()
        assertEquals(listOf(RoutineSection.WakeUp), sections)
    }

    @Test
    fun `the wake-up section shows an alarm with its time and days`() {
        show(state(section = RoutineSection.WakeUp, alarms = alarms))
        compose.onNodeWithText("Weekdays").assertExists()
        compose.onNodeWithText("07:05 · Mon–Fri").assertExists()
        compose.onNodeWithText("Evening lights").assertDoesNotExist()
    }

    @Test
    fun `an alarm switch toggles and the row opens the editor`() {
        show(state(section = RoutineSection.WakeUp, alarms = alarms))
        compose.onNodeWithTag("alarm_switch_w1").performClick()
        assertEquals(listOf("w1" to false), toggled)
        compose.onNodeWithTag("alarm_row_w1").performClick()
        assertEquals(listOf("w1"), tapped)
    }

    @Test
    fun `an unreadable alarm says so`() {
        show(state(section = RoutineSection.WakeUp, alarms = alarms))
        compose.onNodeWithText(text(Res.string.wake_up_unreadable)).assertExists()
    }

    @Test
    fun `no alarms shows the empty copy`() {
        show(state(section = RoutineSection.WakeUp))
        compose.onNodeWithText(text(Res.string.wake_up_empty_title)).assertExists()
        compose.onNodeWithText(text(Res.string.wake_up_empty_body)).assertExists()
    }

    @Test
    fun `the editor sheet shows the days and reports a toggle`() {
        val editor = AlarmEditor("w1", "Weekdays", 7, 5, setOf(1, 2, 3, 4, 5))
        show(state(section = RoutineSection.WakeUp, alarms = alarms, editor = editor))
        compose.onNodeWithTag("alarm_editor").assertExists()
        for (d in 1..5) compose.onNodeWithTag("alarm_day_$d").assertIsSelected()
        compose.onNodeWithTag("alarm_day_0").assertIsNotSelected()
        compose.onNodeWithTag("alarm_day_6").performClick()
        assertEquals(listOf(6), dayToggles)
        compose.onNodeWithTag("alarm_save").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `save is disabled with no day and an error is shown`() {
        val editor = AlarmEditor("w1", "Weekdays", 7, 5, emptySet(), error = Res.string.wake_up_save_failed)
        show(state(section = RoutineSection.WakeUp, alarms = alarms, editor = editor))
        compose.onNodeWithTag("alarm_save").assertIsNotEnabled()
        compose.onNodeWithText(text(Res.string.wake_up_pick_a_day)).assertExists()
        compose.onNodeWithText(text(Res.string.wake_up_save_failed)).assertExists()
    }

    @Test
    fun `placeholders while loading and no empty copy`() {
        show(state(isLoading = true, rows = emptyList()))
        // The placeholder block is one merged node announced as "loading", so its tiles only show in the unmerged tree.
        assertTrue(compose.onAllNodesWithTag("automations-placeholder", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription(text(Res.string.automations_loading)).assertExists()
        compose.onNodeWithText(text(Res.string.automations_empty_title)).assertDoesNotExist()
    }

    @Test
    fun `empty copy when settled with no rows`() {
        show(state(rows = emptyList()))
        compose.onNodeWithText(text(Res.string.automations_empty_title)).assertExists()
        compose.onNodeWithText(text(Res.string.automations_empty_body)).assertExists()
        assertTrue(compose.onAllNodesWithTag("automations-placeholder").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `offline with no rows shows the banner but no empty copy`() {
        show(state(isOffline = true, rows = emptyList()))
        compose.onNodeWithText(text(Res.string.automations_offline_banner)).assertExists()
        compose.onNodeWithText(text(Res.string.automations_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(text(Res.string.automations_empty_body)).assertDoesNotExist()
    }

    @Test
    fun `renders title, chips and rows with area labels`() {
        show(state())
        compose.onNodeWithText(text(Res.string.automations_title)).assertExists()
        compose.onNodeWithText(text(Res.string.automations_chip_all)).assertExists()
        compose.onNodeWithText("Evening lights").assertExists()
        compose.onNodeWithText("Close shutters").assertExists()
        compose.onNodeWithText("Vacation mode").assertExists()
        // "Living room" shows as both a chip and the first row's area; Unassigned as a chip and the second row's area.
        assertEquals(2, compose.onAllNodes(androidx.compose.ui.test.hasText("Living room")).fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodes(androidx.compose.ui.test.hasText(text(Res.string.automations_chip_unassigned))).fetchSemanticsNodes().size)
        compose.onNodeWithText(text(Res.string.automations_empty_title)).assertDoesNotExist()
    }

    @Test
    fun `selecting a chip reports its key`() {
        show(state())
        compose.onNodeWithText(text(Res.string.automations_chip_all)).performClick()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Living room") and androidx.compose.ui.test.hasClickAction())[0].performClick()
        assertEquals(listOf(ALL_KEY, "living"), selected)
    }

    @Test
    fun `offline banner shows and switches are disabled`() {
        show(state(isOffline = true, rows = rows.map { it.copy(toggleEnabled = false) }))
        compose.onNodeWithText(text(Res.string.automations_offline_banner)).assertExists()
        for (i in rows.indices) compose.onAllNodes(isToggleable())[i].assertIsNotEnabled()
    }

    @Test
    fun `online has no banner and enabled switches`() {
        show(state())
        compose.onNodeWithText(text(Res.string.automations_offline_banner)).assertDoesNotExist()
        compose.onAllNodes(isToggleable())[0].assertIsEnabled()
    }

    @Test
    fun `row switch is named by the flow name`() {
        show(state())
        compose.onNode(isToggleable() and androidx.compose.ui.test.hasText("Evening lights")).assertExists()
    }

    @Test
    fun `clicking a switch requests the opposite state`() {
        show(state())
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[1].performClick()
        assertEquals(listOf("a" to false, "b" to true), toggled)
    }

    @Test
    fun `toast is shown once then acknowledged`() {
        show(state(toast = Res.string.automations_toggle_failed))
        compose.waitForIdle()
        compose.onNodeWithText(text(Res.string.automations_toggle_failed)).assertExists()
        assertEquals(1, toastShown)
    }

    @Test
    @Config(qualifiers = "en-w411dp-h914dp-xxhdpi")
    fun `screenshot loaded light`() {
        show(state(), dark = false)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(300)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/automations-loaded-light.png").also { it.parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val seen = HashSet<Int>()
        for (x in 0 until bitmap.width step 7) for (y in 0 until bitmap.height step 7) seen += bitmap.getPixel(x, y)
        assertTrue(seen.size > 1, "blank render")
    }

    /** The loading placeholders drawn now and again 450 ms later (half a pulse), with the clock frozen. */
    private fun placeholderFrames(animatorScale: Float): Pair<IntArray, IntArray> {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
        compose.mainClock.autoAdvance = false
        show(state(isLoading = true, rows = emptyList()))
        compose.mainClock.advanceTimeBy(100)
        fun frame(): IntArray {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        }
        val first = frame()
        compose.mainClock.advanceTimeBy(450)
        return first to frame()
    }

    @Test
    fun `the loading placeholders pulse`() {
        val (a, b) = placeholderFrames(animatorScale = 1f)
        assertFalse(a.contentEquals(b), "the placeholders did not pulse: this test could not see motion")
    }

    @Test
    fun `with animations off the loading placeholders are drawn still`() {
        val (a, b) = placeholderFrames(animatorScale = 0f)
        assertTrue(a.contentEquals(b), "the placeholders pulsed with animations off")
    }
}
