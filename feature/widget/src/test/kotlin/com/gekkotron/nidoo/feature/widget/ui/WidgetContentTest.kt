package com.gekkotron.nidoo.feature.widget.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.feature.widget.TileAction
import com.gekkotron.nidoo.feature.widget.model.LinkStatus
import com.gekkotron.nidoo.feature.widget.model.TileIcon
import com.gekkotron.nidoo.feature.widget.model.TileSnapshot
import com.gekkotron.nidoo.feature.widget.model.TileStatus
import com.gekkotron.nidoo.feature.widget.model.TileValue
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetContentTest {
    private val now = 10_000_000L
    private val none: Action = actionStartActivity(ComponentName("test", "test.Noop"))
    private val noop = object : WidgetActions {
        override fun tap(action: TileAction): Action = none
        override fun refresh(): Action = none
        override fun reconfigure(): Action = none
        override fun openApp(): Action = none
        override fun openSettings(): Action = none
    }

    private fun tile(id: String, name: String, kind: Kind, value: TileValue, f: (TileSnapshot) -> TileSnapshot = { it }) =
        f(TileSnapshot(id, name, kind, TileIcon.SWITCH, value, updatedAt = now))

    private fun state(vararg tiles: TileSnapshot?, link: LinkStatus = LinkStatus.FRESH, configured: Boolean = true) =
        WidgetUiState(tiles.toList(), null, link, lastFresh = now - 60_000, now = now, configured = configured)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun small_size_shows_first_tile_only() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(110.dp, 48.dp))
        provideComposable {
            WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), tile("b", "Lamp", Kind.LIGHT, TileValue.Light(false, null))), noop)
        }
        onNode(hasText("Kettle")).assertExists()
        onNode(hasText("Lamp")).assertDoesNotExist()
    }

    @Test
    fun single_tile_shows_value_and_name_without_footer() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 60.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true))), noop) }
        onNode(hasText("Kettle")).assertExists()
        onNode(hasText("On")).assertExists()
        onNode(hasText("Updated")).assertDoesNotExist()
    }

    @Test
    fun single_row_tiles_carry_the_refresh_time() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 60.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true))), noop) }
        val since = TileText.since(now - 60_000, now, context.resources.configuration.locales[0])
        onNode(hasText("On · $since")).assertExists()
    }

    @Test
    fun cover_buttons_show_on_a_wide_single_tile() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(320.dp, 60.dp))
        provideComposable { WidgetContent(state(tile("b", "Blind", Kind.COVER, TileValue.Cover(CoverState.OPEN, 60))), noop) }
        onNode(hasText("Stop")).assertExists()
        onNode(hasText("Close")).assertExists()
    }

    @Test
    fun cover_buttons_hide_when_they_would_squeeze_the_text() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 60.dp))
        provideComposable { WidgetContent(state(tile("b", "Blind", Kind.COVER, TileValue.Cover(CoverState.OPEN, 60))), noop) }
        onNode(hasText("Blind")).assertExists()
        onNode(hasText("Stop")).assertDoesNotExist()
    }

    @Test
    fun a_wide_one_row_widget_shows_several_tiles() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(320.dp, 60.dp))
        provideComposable {
            WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), tile("b", "Lamp", Kind.LIGHT, TileValue.Light(false, null))), noop)
        }
        onNode(hasText("Kettle")).assertExists()
        onNode(hasText("Lamp")).assertExists()
        onNode(hasText("Updated")).assertDoesNotExist()
    }

    @Test
    fun the_biggest_size_shows_all_eight_tiles() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(400.dp, 400.dp))
        val names = (1..8).map { "Device $it" }
        provideComposable { WidgetContent(state(*names.map { tile(it, it, Kind.SWITCH, TileValue.OnOff(false)) }.toTypedArray()), noop) }
        names.forEach { onNode(hasText(it)).assertExists() }
        onNode(hasText("Updated")).assertExists()
    }

    @Test
    fun large_size_shows_all_tiles_and_footer() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), tile("b", "Blind", Kind.COVER, TileValue.Cover(CoverState.OPEN, 60))), noop)
        }
        onNode(hasText("Kettle")).assertExists()
        onNode(hasText("Open 60 %")).assertExists()
        onNode(hasText("Updated")).assertExists()
    }

    @Test
    fun missing_tile_shows_dash_and_removed_shows_removed() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetContent(state(null, tile("b", "Old", Kind.SWITCH, TileValue.OnOff(true)) { it.copy(status = TileStatus.REMOVED) }), noop)
        }
        onNode(hasText("Removed")).assertExists()
        onNode(hasText("Old")).assertExists()
    }

    @Test
    fun stale_tile_says_when_it_was_last_updated() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        val old = now - TileSnapshot.STALE_MS - 1
        provideComposable {
            WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), tile("b", "Lamp", Kind.SWITCH, TileValue.OnOff(false)) { it.copy(updatedAt = old) }), noop)
        }
        val since = TileText.since(old, now, context.resources.configuration.locales[0])
        onNode(hasText("Off · $since")).assertExists()
        onNode(hasText("On")).assertExists()
    }

    @Test
    fun not_configured_shows_set_up() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), link = LinkStatus.NOT_CONFIGURED), noop) }
        onNode(hasText("Set up Nidoo")).assertExists()
        onNode(hasText("Kettle")).assertDoesNotExist()
    }

    @Test
    fun empty_config_shows_hint() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(state(configured = false), noop) }
        onNode(hasText("Long-press to choose devices")).assertExists()
    }

    @Test
    fun failed_tile_shows_marker() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)) { it.copy(status = TileStatus.FAILED) }), noop) }
        onNode(hasText("!")).assertExists()
    }

    @Test
    fun pending_tile_shows_ellipsis() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)) { it.copy(status = TileStatus.PENDING, pendingSince = now) }), noop) }
        onNode(hasText("…")).assertExists()
    }

    @Test
    fun offline_footer() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), link = LinkStatus.OFFLINE), noop) }
        onNode(hasText("Offline")).assertExists()
    }

    @Test
    fun auth_failure_footer() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(state(tile("a", "Kettle", Kind.SWITCH, TileValue.OnOff(true)), link = LinkStatus.AUTH_FAILED), noop) }
        onNode(hasText("Sign-in failed")).assertExists()
    }
}

class TileGridTest {
    @Test
    fun a_narrow_widget_keeps_one_column_instead_of_squeezing_two() {
        assertEquals(1, tileGrid(180.dp, 180.dp, 4).columns)
    }

    @Test
    fun a_wider_widget_gets_more_columns() {
        assertEquals(2, tileGrid(250.dp, 250.dp, 8).columns)
        assertEquals(3, tileGrid(400.dp, 250.dp, 8).columns)
    }

    @Test
    fun fewer_tiles_than_columns_share_the_width() {
        val grid = tileGrid(400.dp, 250.dp, 2)
        assertEquals(2, grid.columns)
        assertEquals(1, grid.rows)
    }

    @Test
    fun tiles_grow_with_the_widget_but_not_without_limit() {
        val small = tileGrid(250.dp, 180.dp, 4)
        val tall = tileGrid(250.dp, 400.dp, 4)
        assertTrue(tall.tileHeight > small.tileHeight)
        assertTrue(tall.tileHeight <= 84.dp)
    }

    @Test
    fun tiles_that_fit_grow_with_the_size() {
        assertEquals(1, tilesThatFit(110.dp, 48.dp))
        assertEquals(2, tilesThatFit(250.dp, 60.dp))
        assertTrue(tilesThatFit(250.dp, 250.dp) >= 4)
        assertEquals(8, tilesThatFit(400.dp, 400.dp))
    }

    @Test
    fun one_row_high_widgets_have_a_single_row() {
        val grid = tileGrid(320.dp, 60.dp, 8)
        assertTrue(grid.singleRow)
        assertEquals(1, grid.rows)
    }
}

@RunWith(AndroidJUnit4::class)
class TileIsOnTest {
    private val now = 10_000_000L
    private fun tile(kind: Kind, value: TileValue, f: (TileSnapshot) -> TileSnapshot = { it }) =
        f(TileSnapshot("a", "A", kind, TileIcon.SWITCH, value, updatedAt = now))

    @Test
    fun live_on_values_take_the_accent() {
        assert(tile(Kind.LIGHT, TileValue.Light(true, 40)).isOn(now))
        assert(tile(Kind.SWITCH, TileValue.OnOff(true)).isOn(now))
        assert(tile(Kind.COVER, TileValue.Cover(CoverState.OPEN, 100)).isOn(now))
        // A shutter stopped midway is open, even one that reports no position.
        assert(tile(Kind.COVER, TileValue.Cover(CoverState.STOPPED, null)).isOn(now))
        assert(tile(Kind.ALARM_PANEL, TileValue.Alarm(com.gekkotron.nidoo.model.AlarmState.ARMED_AWAY)).isOn(now))
    }

    @Test
    fun off_stale_unavailable_and_sensors_stay_neutral() {
        assert(!tile(Kind.LIGHT, TileValue.Light(false, null)).isOn(now))
        assert(!tile(Kind.COVER, TileValue.Cover(CoverState.CLOSED, 0)).isOn(now))
        assert(!tile(Kind.ALARM_PANEL, TileValue.Alarm(com.gekkotron.nidoo.model.AlarmState.DISARMED)).isOn(now))
        assert(!tile(Kind.BINARY_SENSOR, TileValue.OnOff(true)).isOn(now))
        assert(!tile(Kind.SWITCH, TileValue.OnOff(true)) { it.copy(updatedAt = now - TileSnapshot.STALE_MS - 1) }.isOn(now))
        assert(!tile(Kind.SWITCH, TileValue.OnOff(true)) { it.copy(available = false) }.isOn(now))
    }
}
