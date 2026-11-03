package com.gekkotron.nidoo.feature.widget.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.gekkotron.nidoo.feature.widget.R
import com.gekkotron.nidoo.feature.widget.TileAction
import com.gekkotron.nidoo.feature.widget.TileActions
import com.gekkotron.nidoo.feature.widget.model.LinkStatus
import com.gekkotron.nidoo.feature.widget.model.TileIcon
import com.gekkotron.nidoo.feature.widget.model.TileSnapshot
import com.gekkotron.nidoo.feature.widget.model.TileStatus
import com.gekkotron.nidoo.feature.widget.model.TileValue
import com.gekkotron.nidoo.feature.widget.model.WidgetCommand
import com.gekkotron.nidoo.feature.widget.model.WidgetConfig
import com.gekkotron.nidoo.model.AlarmMode
import com.gekkotron.nidoo.model.AlarmState
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.Kind
import java.text.DateFormat
import java.util.Date

data class WidgetUiState(
    /** One entry per configured id, in order; null = never seen. */
    val tiles: List<TileSnapshot?>,
    val armMode: AlarmMode?,
    val link: LinkStatus,
    val lastFresh: Long?,
    val now: Long,
    val configured: Boolean,
    val voiceEnabled: Boolean = false,
)

/** Turns what a tap means into a Glance [Action]; the widget supplies the Android side. */
interface WidgetActions {
    fun tap(action: TileAction): Action

    fun refresh(): Action

    fun reconfigure(): Action

    fun openApp(): Action

    fun openSettings(): Action

    /** Opens the voice sheet; defaults to the app for hosts that have none. */
    fun openVoice(): Action = openApp()
}

/** Below this height the widget is one row of tiles with no footer (widgets spec §2.2). */
private val SINGLE_ROW_MAX_HEIGHT = 100.dp

/** Space around the content, shared by the layout and by [tilesThatFit]. */
private val PAD = 10.dp
private val SINGLE_ROW_PAD = 6.dp
private val FOOTER = 28.dp
private val GAP = 6.dp

/** The smallest tile that still reads: below this the grid drops a column (or a row) rather than squeeze its text. */
private val MIN_TILE_WIDTH = 104.dp
private val MIN_TILE_HEIGHT = 52.dp

/** Tiles grow to fill a tall widget, up to this. */
private val MAX_TILE_HEIGHT = 84.dp
private const val MAX_COLUMNS = 3

/** Enough room for the icon, the value and a cover tile's Open / Stop / Close buttons side by side. */
private val COVER_BUTTONS_MIN_WIDTH = 280.dp

/** How the tiles are laid out at a given widget size: [count] tiles in [columns] × [rows], each [tileWidth] × [tileHeight]. */
internal data class TileGrid(val columns: Int, val rows: Int, val count: Int, val tileWidth: Dp, val tileHeight: Dp, val singleRow: Boolean)

private fun columnsFor(innerWidth: Dp): Int = ((innerWidth + GAP) / (MIN_TILE_WIDTH + GAP)).toInt().coerceIn(1, MAX_COLUMNS)

private fun rowsFor(innerHeight: Dp): Int = ((innerHeight + GAP) / (MIN_TILE_HEIGHT + GAP)).toInt().coerceIn(1, WidgetConfig.MAX_ENTITIES)

/**
 * Lays out [tiles] tiles in a widget of [width] × [height]: as many columns as fit [MIN_TILE_WIDTH], as many rows as fit
 * [MIN_TILE_HEIGHT], and tiles stretched to share the space so a bigger widget gets bigger tiles rather than empty room.
 */
internal fun tileGrid(width: Dp, height: Dp, tiles: Int): TileGrid {
    val singleRow = height < SINGLE_ROW_MAX_HEIGHT
    val innerWidth = width - (if (singleRow) SINGLE_ROW_PAD else PAD) * 2
    val innerHeight = if (singleRow) height - SINGLE_ROW_PAD * 2 else height - PAD * 2 - FOOTER
    val maxColumns = columnsFor(innerWidth)
    val maxRows = if (singleRow) 1 else rowsFor(innerHeight)
    val count = tiles.coerceIn(1, maxColumns * maxRows)
    // Fewer tiles than columns: let them share the width instead of leaving a gap on the right.
    val columns = minOf(maxColumns, count)
    val rows = (count + columns - 1) / columns
    val tileWidth = (innerWidth - GAP * (columns - 1)) / columns
    val tileHeight = if (singleRow) innerHeight else ((innerHeight - GAP * (rows - 1)) / rows).coerceAtMost(MAX_TILE_HEIGHT)
    return TileGrid(columns, rows, count, tileWidth, tileHeight, singleRow)
}

/** How many tiles the widget shows at a size; the config screen uses the same rule. */
fun tilesThatFit(width: Dp, height: Dp): Int = tileGrid(width, height, WidgetConfig.MAX_ENTITIES).count

/** Sizes inside one tile, picked from the tile's own size so text never gets squeezed out by the icon or the buttons. */
private data class TileStyle(
    val chip: Dp,
    val icon: Dp,
    val padding: Dp,
    val iconGap: Dp,
    /** Value above name (one tile on its own, or a roomy tile); otherwise name above value. */
    val valueFirst: Boolean,
    val valueSize: TextUnit,
    val nameSize: TextUnit,
    val coverButtons: Boolean,
)

private fun tileStyle(width: Dp, height: Dp, alone: Boolean): TileStyle {
    val roomy = width >= 150.dp && height >= 64.dp
    val narrow = width < 130.dp
    val chip = when {
        roomy -> 44.dp
        narrow -> 28.dp
        else -> 34.dp
    }.coerceAtMost(height - 12.dp).coerceAtLeast(20.dp)
    return TileStyle(
        chip = chip,
        icon = chip * 0.55f,
        padding = if (roomy) 14.dp else if (narrow) 8.dp else 10.dp,
        iconGap = if (roomy) 12.dp else if (narrow) 8.dp else 10.dp,
        valueFirst = roomy || alone,
        valueSize = when {
            roomy -> 20.sp
            alone && !narrow -> 17.sp
            narrow -> 14.sp
            else -> 15.sp
        },
        nameSize = if (roomy) 12.sp else 11.sp,
        coverButtons = width >= COVER_BUTTONS_MIN_WIDTH,
    )
}

/**
 * Nidoo colours for text and icons (day = Ivoire, night = Night Glass), explicit rather than Glance's dynamic colours so
 * the widget keeps the app's identity and an on/off accent. The surfaces are shape drawables over `res/values{,-night}/colors.xml`
 * (they also give rounded corners below API 31); keep the two in step.
 */
private fun tone(day: Long, night: Long) = ColorProvider(day = Color(day), night = Color(night))

private object Palette {
    val content = tone(0xFF1F2420, 0xFFE8ECFF)
    val contentOn = tone(0xFF5C4208, 0xFFFFF3DC)
    val muted = tone(0xFF5F665C, 0xFFA3A9C6)
    val mutedOn = tone(0xFF8A5F12, 0xFFE0CFA8)
    val dim = tone(0xFFA09A90, 0xFF6F7694)
    val icon = tone(0xFF5F665C, 0xFFA3A9C6)
    val iconOn = tone(0xFF2F4A2B, 0xFFFFC457)
    val error = tone(0xFFA23B4F, 0xFFFF9AB2)
    val onError = tone(0xFFFFFFFF, 0xFF10172E)
}

/** A tile reads "on" (accent surface) only when its value is live and says so; stale or missing never glows. */
internal fun TileSnapshot.isOn(now: Long): Boolean {
    if (!available || isStale(now) || effectiveStatus(now) == TileStatus.REMOVED) return false
    return when (val v = value) {
        is TileValue.Light -> v.on
        is TileValue.OnOff -> kind == Kind.SWITCH && v.on
        is TileValue.Cover -> v.state == CoverState.OPEN || v.state == CoverState.OPENING || v.state == CoverState.CLOSING ||
            (v.state == CoverState.STOPPED && v.position != 0)
        is TileValue.Climate -> v.mode != null && v.mode != "off"
        is TileValue.Alarm -> v.state != AlarmState.DISARMED && v.state != AlarmState.DISARMING
        else -> false
    }
}

@Composable
fun WidgetContent(state: WidgetUiState, actions: WidgetActions) {
    val context = LocalContext.current
    val size = LocalSize.current
    val grid = tileGrid(size.width, size.height, state.tiles.size)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(if (grid.singleRow) SINGLE_ROW_PAD else PAD)) {
        when {
            state.link == LinkStatus.NOT_CONFIGURED -> Message(R.drawable.ic_widget_home, context.getString(R.string.widget_set_up), actions.openApp())
            !state.configured -> Message(R.drawable.ic_widget_pick, context.getString(R.string.widget_empty), actions.reconfigure())
            grid.singleRow -> Grid(state, actions, grid)
            else -> {
                Grid(state, actions, grid)
                // The footer stays at the bottom however tall the widget is.
                Spacer(GlanceModifier.defaultWeight())
                Footer(state, actions)
            }
        }
    }
}

@Composable
private fun Grid(state: WidgetUiState, actions: WidgetActions, grid: TileGrid) {
    val style = tileStyle(grid.tileWidth, grid.tileHeight, alone = grid.count == 1)
    val rows = state.tiles.take(grid.count).chunked(grid.columns)
    // The gap is padding inside each row, not a Spacer between rows: Glance caps a Column at 10 children.
    Column(GlanceModifier.fillMaxWidth()) {
        rows.forEachIndexed { r, row ->
            val last = r == rows.lastIndex
            Row(
                GlanceModifier.fillMaxWidth().height(if (last) grid.tileHeight else grid.tileHeight + GAP).padding(bottom = if (last) 0.dp else GAP),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEachIndexed { i, tile ->
                    if (i > 0) Spacer(GlanceModifier.width(GAP))
                    Tile(tile, state, actions, style, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                }
                // Keep a short last row the width of a full tile, not stretched across the grid.
                repeat(grid.columns - row.size) {
                    Spacer(GlanceModifier.width(GAP))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: TileSnapshot?, state: WidgetUiState, actions: WidgetActions, style: TileStyle, modifier: GlanceModifier) {
    val context = LocalContext.current
    val status = tile?.effectiveStatus(state.now)
    val dim = tile == null || tile.isStale(state.now) || !tile.available
    val on = tile?.isOn(state.now) == true
    val text = when {
        tile == null -> "—"
        status == TileStatus.REMOVED -> context.getString(R.string.widget_removed)
        status == TileStatus.PENDING -> "…"
        else -> {
            val locale = context.resources.configuration.locales[0]
            val value = TileText.of(context, tile.value, locale)
            // A stale value says when it was last live, so a cached tile is not read as the current state.
            val since = tile.updatedAt?.takeIf { tile.isStale(state.now) }?.let { TileText.since(it, state.now, locale) }
            since?.let { "$value · $it" } ?: value
        }
    }
    val tap = tile?.let { actions.tap(TileActions.forTap(it, state.armMode)) } ?: actions.openApp()
    val valueColor = when {
        on -> Palette.contentOn
        dim -> Palette.dim
        else -> Palette.content
    }
    val nameColor = if (on) Palette.mutedOn else if (dim) Palette.dim else Palette.muted
    val iconColor = if (on) Palette.iconOn else if (dim) Palette.dim else Palette.icon
    val surface = if (on) R.drawable.widget_tile_on else R.drawable.widget_tile
    val chip = if (on) R.drawable.widget_chip_on else R.drawable.widget_chip
    val name = tile?.name ?: "—"
    val coverButtons = style.coverButtons && tile != null && tile.kind == Kind.COVER && status != TileStatus.REMOVED

    Row(
        modifier.background(ImageProvider(surface)).padding(horizontal = style.padding).clickable(tap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(iconRes(tile?.icon), chip, iconColor, chipSize = style.chip, iconSize = style.icon)
        Spacer(GlanceModifier.width(style.iconGap))
        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            val value = TextStyle(color = valueColor, fontWeight = FontWeight.Bold, fontSize = style.valueSize)
            val label = TextStyle(color = nameColor, fontSize = style.nameSize)
            if (style.valueFirst) {
                Text(text, maxLines = 1, style = value)
                Text(name, maxLines = 1, style = label)
            } else {
                Text(name, maxLines = 1, style = label)
                Text(text, maxLines = 1, style = value)
            }
        }
        if (status == TileStatus.FAILED) {
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.size(18.dp).background(ImageProvider(R.drawable.widget_badge)), contentAlignment = Alignment.Center) {
                Text("!", style = TextStyle(color = Palette.onError, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center))
            }
        }
        if (coverButtons && tile != null) {
            Spacer(GlanceModifier.width(8.dp))
            CoverButtons(tile, actions)
        }
    }
}

@Composable
private fun IconChip(@DrawableRes icon: Int, @DrawableRes chip: Int, tint: ColorProvider, chipSize: Dp, iconSize: Dp) {
    Box(GlanceModifier.size(chipSize).background(ImageProvider(chip)), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = GlanceModifier.size(iconSize))
    }
}

@Composable
private fun CoverButtons(tile: TileSnapshot, actions: WidgetActions) {
    val context = LocalContext.current
    val buttons = listOf(
        WidgetCommand.Open to R.string.widget_cover_open,
        WidgetCommand.Stop to R.string.widget_cover_stop,
        WidgetCommand.Close to R.string.widget_cover_close,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        buttons.forEachIndexed { i, (command, label) ->
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            Box(
                GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(actions.tap(TileActions.coverButton(tile, command))),
                contentAlignment = Alignment.Center,
            ) {
                Text(context.getString(label), maxLines = 1, style = TextStyle(color = Palette.content, fontWeight = FontWeight.Medium, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun Footer(state: WidgetUiState, actions: WidgetActions) {
    val context = LocalContext.current
    val time = state.lastFresh?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    val label: String
    val onClick: Action
    var problem = false
    when (state.link) {
        LinkStatus.OFFLINE -> {
            label = time?.let { context.getString(R.string.widget_offline, it) } ?: context.getString(R.string.widget_offline_never)
            onClick = actions.refresh()
            problem = true
        }
        LinkStatus.AUTH_FAILED -> {
            label = context.getString(R.string.widget_sign_in_failed)
            onClick = actions.openSettings()
            problem = true
        }
        else -> {
            label = time?.let { context.getString(R.string.widget_updated, it) }.orEmpty()
            onClick = actions.refresh()
        }
    }
    val tone = if (problem) Palette.error else Palette.dim
    // The whole strip is the touch target (full width, padded top and bottom), not only the 14dp glyph.
    Row(GlanceModifier.fillMaxWidth().height(FOOTER).padding(horizontal = 6.dp).clickable(onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, maxLines = 1, style = TextStyle(color = tone, fontSize = 10.sp), modifier = GlanceModifier.defaultWeight())
        if (state.voiceEnabled) {
            Image(
                ImageProvider(R.drawable.ic_widget_mic),
                contentDescription = context.getString(R.string.widget_voice),
                colorFilter = ColorFilter.tint(Palette.icon),
                modifier = GlanceModifier.size(20.dp).clickable(actions.openVoice()),
            )
            Spacer(GlanceModifier.width(14.dp))
        }
        Image(
            ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = context.getString(R.string.widget_refresh),
            colorFilter = ColorFilter.tint(tone),
            modifier = GlanceModifier.size(16.dp),
        )
    }
}

@Composable
private fun Message(@DrawableRes icon: Int, text: String, onClick: Action) {
    Box(GlanceModifier.fillMaxSize().clickable(onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconChip(icon, R.drawable.widget_chip, Palette.icon, chipSize = 40.dp, iconSize = 22.dp)
            Spacer(GlanceModifier.height(8.dp))
            Text(text, maxLines = 2, style = TextStyle(color = Palette.content, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
        }
    }
}

private fun iconRes(icon: TileIcon?): Int = when (icon) {
    TileIcon.LIGHT -> R.drawable.ic_widget_light
    TileIcon.SWITCH -> R.drawable.ic_widget_switch
    TileIcon.COVER -> R.drawable.ic_widget_cover
    TileIcon.CLIMATE -> R.drawable.ic_widget_climate
    TileIcon.TEMPERATURE -> R.drawable.ic_widget_temperature
    TileIcon.ALARM -> R.drawable.ic_widget_alarm
    TileIcon.BUTTON -> R.drawable.ic_widget_button
    TileIcon.SENSOR, null -> R.drawable.ic_widget_sensor
}
