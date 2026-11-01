package com.gekkotron.oikos.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.oikos.dashboards.WidgetKind
import com.gekkotron.oikos.dashboards.WidgetRange
import com.gekkotron.oikos.designsystem.OikosDimens
import com.gekkotron.oikos.designsystem.OikosTheme
import com.gekkotron.oikos.designsystem.charts.ChartPoint
import com.gekkotron.oikos.designsystem.charts.HistoryLineChart
import com.gekkotron.oikos.designsystem.components.OikosCard
import com.gekkotron.oikos.designsystem.components.PendingDot
import com.gekkotron.oikos.designsystem.icons.OikosGlyph
import com.gekkotron.oikos.designsystem.icons.StaticIcon
import com.gekkotron.oikos.feature.energy.presentation.EnergyFormat
import com.gekkotron.oikos.feature.home.R
import com.gekkotron.oikos.feature.home.presentation.CardUi
import com.gekkotron.oikos.feature.home.presentation.EnergyMetric
import com.gekkotron.oikos.feature.home.presentation.EnergyValue
import com.gekkotron.oikos.feature.home.presentation.TileContent
import com.gekkotron.oikos.feature.home.presentation.TileUi
import com.gekkotron.oikos.model.Command
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/** The tiles of a hub dashboard as grid items; the grid's own column count caps each tile's span. */
internal fun LazyGridScope.dashboardTiles(
    tiles: List<TileUi>,
    running: Set<String>,
    onEntityClick: (CardUi) -> Unit,
    onEntityLongClick: (CardUi) -> Unit,
    onSendGroup: (String, Command) -> Unit,
    onForce: (CardUi) -> Unit,
    onRunFlow: (String) -> Unit,
    onOpenEnergy: () -> Unit,
    onOpenHistory: (String) -> Unit,
) {
    items(
        tiles,
        key = { it.key },
        span = { GridItemSpan(if (it.fullWidth) maxLineSpan else min(it.columns, maxLineSpan)) },
    ) { tile ->
        val modifier = if (tile.rows > 1) {
            Modifier.heightIn(min = OikosDimens.cardMinHeight * tile.rows + OikosDimens.gridGap * (tile.rows - 1))
        } else {
            Modifier
        }
        when (val content = tile.content) {
            is TileContent.Entity ->
                TileCard(content.card, modifier, onEntityClick, onEntityLongClick, onSendGroup, onForce)
            is TileContent.Room -> Column(modifier, verticalArrangement = Arrangement.spacedBy(OikosDimens.gridGap)) {
                Text(content.title, style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted, modifier = Modifier.semantics { heading() })
                content.cards.forEach { TileCard(it, Modifier, onEntityClick, onEntityLongClick, onSendGroup, onForce) }
            }
            is TileContent.FlowButton -> FlowTile(content, content.flowId in running, modifier, onRunFlow)
            is TileContent.EnergyStat -> EnergyTile(content, modifier, onOpenEnergy)
            is TileContent.History -> HistoryTile(content, modifier, onOpenHistory)
            is TileContent.Placeholder -> PlaceholderTile(content, modifier)
            is TileContent.Unavailable -> UnavailableTile(content, modifier)
        }
    }
}

/** Home's own click and long-press branching lives in HomeScreen; a tile card only forwards to it. */
@Composable
private fun TileCard(
    card: CardUi,
    modifier: Modifier,
    onClick: (CardUi) -> Unit,
    onLongClick: (CardUi) -> Unit,
    onSendGroup: (String, Command) -> Unit,
    onForce: (CardUi) -> Unit,
) {
    if (card.isGroup) {
        GroupCard(card, onClick = { onClick(card) }, onLongClick = { onLongClick(card) }, onSendGroup = onSendGroup, modifier = modifier)
    } else {
        EntityCard(card, onClick = { onClick(card) }, onLongClick = { onLongClick(card) }, onForce = { onForce(card) }, modifier = modifier)
    }
}

@Composable
private fun FlowTile(flow: TileContent.FlowButton, running: Boolean, modifier: Modifier, onRunFlow: (String) -> Unit) {
    OikosCard(modifier = modifier.fillMaxWidth(), enabled = flow.enabled) {
        Text(flow.name, style = OikosTheme.type.cardName, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!flow.enabled) {
            Text(stringResource(R.string.widget_flow_off), style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted)
        }
        TextButton(
            onClick = { onRunFlow(flow.flowId) },
            enabled = flow.enabled && !running,
            modifier = Modifier.testTag("flow-${flow.flowId}"),
        ) {
            if (running) PendingDot() else Text(stringResource(R.string.widget_flow_run))
        }
    }
}

@Composable
private fun EnergyTile(stat: TileContent.EnergyStat, modifier: Modifier, onOpenEnergy: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val label = stat.title ?: stringResource(metricLabel(stat.metric))
    val value = when (val v = stat.value) {
        is EnergyValue.Kwh -> EnergyFormat.energy(v.v, locale)
        is EnergyValue.Watts -> EnergyFormat.power(v.v, locale)
        is EnergyValue.Percent -> "%d %%".format(locale, v.v)
        null -> "—"
    }
    OikosCard(modifier = modifier.fillMaxWidth().testTag("energy-${stat.metric.wire}"), onClick = onOpenEnergy) {
        Text(label, style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, style = OikosTheme.type.cardValue)
    }
}

private fun metricLabel(metric: EnergyMetric): Int = when (metric) {
    EnergyMetric.TODAY_KWH -> R.string.widget_energy_today
    EnergyMetric.WEEK_KWH -> R.string.widget_energy_week
    EnergyMetric.MONTH_KWH -> R.string.widget_energy_month
    EnergyMetric.GRID_POWER_NOW -> R.string.widget_energy_grid_now
    EnergyMetric.PV_POWER_NOW -> R.string.widget_energy_solar_now
    EnergyMetric.SELF_CONSUMPTION_PCT -> R.string.widget_energy_self
}

@Composable
private fun HistoryTile(history: TileContent.History, modifier: Modifier, onOpenHistory: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val points = remember(history.points) { history.points.orEmpty().map { ChartPoint(it.t, it.mean, it.min, it.max) } }
    val time = remember(history.range, locale) { timeFormat(history.range, locale) }
    OikosCard(modifier = modifier.fillMaxWidth().testTag("history-${history.entityId}"), onClick = { onOpenHistory(history.entityId) }) {
        Text(history.title, style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        HistoryLineChart(
            points = points,
            color = OikosTheme.colors.water,
            formatValue = { v -> listOfNotNull(String.format(locale, "%.1f", v), history.unit).joinToString(" ") },
            formatTime = time,
            contentDescription = history.title,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun timeFormat(range: WidgetRange, locale: Locale): (Long) -> String {
    val pattern = when (range) {
        WidgetRange.D7, WidgetRange.D30 -> "d MMM"
        else -> "HH:mm"
    }
    val formatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(ZoneId.systemDefault())
    return { seconds -> formatter.format(Instant.ofEpochSecond(seconds)) }
}

@Composable
private fun PlaceholderTile(placeholder: TileContent.Placeholder, modifier: Modifier) {
    val glyph = when (placeholder.kind) {
        WidgetKind.CAMERA -> OikosGlyph.PLAY
        else -> OikosGlyph.SHIELD
    }
    OikosCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StaticIcon(glyph, modifier = Modifier.size(24.dp).clearAndSetSemantics {})
            placeholder.title?.let { Text(it, style = OikosTheme.type.cardName, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Text(stringResource(R.string.widget_open_on_web), style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted)
    }
}

@Composable
private fun UnavailableTile(unavailable: TileContent.Unavailable, modifier: Modifier) {
    OikosCard(modifier = modifier.fillMaxWidth(), enabled = false) {
        unavailable.title?.let { Text(it, style = OikosTheme.type.cardName, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(stringResource(R.string.widget_unavailable), style = OikosTheme.type.label, color = OikosTheme.colors.contentMuted)
    }
}
