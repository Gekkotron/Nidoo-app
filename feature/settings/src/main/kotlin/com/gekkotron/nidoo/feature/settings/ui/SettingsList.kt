package com.gekkotron.nidoo.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.data.ThemePreference
import com.gekkotron.nidoo.designsystem.NidooDimens
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.feature.settings.R
import com.gekkotron.nidoo.feature.settings.presentation.ConnectionSummary
import com.gekkotron.nidoo.feature.settings.presentation.PlatformRow
import com.gekkotron.nidoo.feature.settings.presentation.RowStatus
import com.gekkotron.nidoo.feature.settings.presentation.SettingsPage
import com.gekkotron.nidoo.feature.settings.presentation.SettingsUi
import com.gekkotron.nidoo.transport.LinkState

/** The status column's text for a platform row; empty for [RowStatus.NONE] (the demo has no push word). */
@Composable
private fun RowStatus.text(): String = when (this) {
    RowStatus.NONE -> ""
    RowStatus.ON -> stringResource(R.string.settings_on)
    RowStatus.OFF -> stringResource(R.string.settings_off)
    RowStatus.PUSH_REGISTERED -> stringResource(R.string.settings_push_short_registered)
    RowStatus.PUSH_WAITING -> stringResource(R.string.settings_push_short_waiting)
    RowStatus.PUSH_FAILED -> stringResource(R.string.settings_push_short_failed)
    RowStatus.PUSH_OFF -> stringResource(R.string.settings_push_short_off)
}

@StringRes
internal fun linkWord(link: LinkState): Int = when (link) {
    is LinkState.Connected -> R.string.settings_link_online
    LinkState.Connecting, is LinkState.Reconnecting -> R.string.settings_link_connecting
    is LinkState.Offline -> R.string.settings_link_offline
}

@StringRes
private fun themeName(theme: ThemePreference): Int = when (theme) {
    ThemePreference.SYSTEM -> R.string.settings_theme_system
    ThemePreference.NIGHT_GLASS -> R.string.settings_theme_night
    ThemePreference.IVOIRE -> R.string.settings_theme_ivoire
}

/** The tile accents, as theme tokens so Night Glass and Ivoire each get their own shade. */
private enum class Tone { AMBER, ROSE, CYAN, VIOLET, MINT, MUTED }

@Composable
private fun Tone.color(): Color = with(NidooTheme.colors) {
    when (this@color) {
        Tone.AMBER -> solar
        Tone.ROSE -> offline
        Tone.CYAN -> water
        Tone.VIOLET -> grid
        Tone.MINT -> connected
        Tone.MUTED -> contentMuted
    }
}

/** One list row: where it goes (a page, or Rooms when [page] is null), how it looks, and its status. */
private class Entry(
    val page: SettingsPage?,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val glyph: NidooGlyph,
    val tone: Tone,
    val status: @Composable () -> String,
)

/** How a platform page looks in the list: title, hint, glyph and tone. */
private fun platformEntry(row: PlatformRow): Entry = when (row.page) {
    SettingsPage.NOTIFICATIONS -> Entry(row.page, R.string.settings_notifications, R.string.settings_notifications_hint, NidooGlyph.BELL, Tone.ROSE) { row.status.text() }
    SettingsPage.PRESENCE -> Entry(row.page, R.string.presence_section_title, R.string.settings_presence_hint, NidooGlyph.PIN, Tone.CYAN) { row.status.text() }
    SettingsPage.VOICE -> Entry(row.page, R.string.voice_section_title, R.string.settings_voice_hint, NidooGlyph.MIC, Tone.VIOLET) { row.status.text() }
    else -> error("${row.page} is not a platform page")
}

/** The Settings list (settings sub-pages spec): a header, then the House and App groups. */
@Composable
fun SettingsListScreen(
    state: SettingsUi,
    version: String,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenRooms: () -> Unit,
    onBack: () -> Unit,
) {
    val house = listOf(
        Entry(null, R.string.settings_rooms, R.string.settings_rooms_summary, NidooGlyph.HOUSE, Tone.AMBER) {
            if (state.roomCount > 0) state.roomCount.toString() else ""
        },
    ) + state.platformRows.map(::platformEntry)
    val app = listOf(
        Entry(SettingsPage.APPEARANCE, R.string.settings_appearance, R.string.settings_appearance_hint, NidooGlyph.SUN_MOON, Tone.VIOLET) {
            stringResource(themeName(state.theme))
        },
        Entry(SettingsPage.CONNECTION, R.string.settings_connection, R.string.settings_connection_hint, NidooGlyph.BROKER, Tone.MINT) {
            when (state.connection) {
                null -> ""
                ConnectionSummary.Demo -> stringResource(R.string.settings_demo)
                is ConnectionSummary.Direct -> stringResource(linkWord(state.link))
            }
        },
        Entry(SettingsPage.ABOUT, R.string.settings_about, R.string.settings_about_hint, NidooGlyph.INFO, Tone.MUTED) { version },
    )
    val open: (Entry) -> Unit = { e -> e.page?.let(onOpenPage) ?: onOpenRooms() }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = NidooTheme.colors.content,
        topBar = { SettingsTopBar(null, onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeader(state, version)
            Group(stringResource(R.string.settings_group_house), house, open)
            Group(stringResource(R.string.settings_group_app), app, open)
        }
    }
}

@Composable
private fun SettingsHeader(state: SettingsUi, version: String) {
    val c = NidooTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // The launcher icon's own layers, cropped to its 72/108 safe zone like the adaptive icon.
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.settings_mark_background), null, Modifier.requiredSize(84.dp))
            Image(painterResource(R.drawable.settings_mark_foreground), null, Modifier.requiredSize(84.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_title), style = NidooTheme.type.display, modifier = Modifier.semantics { heading() })
            val wordmark = stringResource(R.string.settings_wordmark)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.content)) { append(wordmark) }
                    append(" ")
                    append(version)
                },
                style = NidooTheme.type.label,
                color = c.contentMuted,
            )
            when (val connection = state.connection) {
                null -> Unit
                ConnectionSummary.Demo -> Text(stringResource(R.string.settings_demo), style = NidooTheme.type.label, color = c.contentMuted)
                is ConnectionSummary.Direct -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dot = when (state.link) {
                        is LinkState.Connected -> c.connected
                        is LinkState.Offline -> c.offline
                        else -> c.warning
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Text(stringResource(linkWord(state.link)), style = NidooTheme.type.label, color = c.contentMuted)
                    Text("·", style = NidooTheme.type.label, color = c.contentMuted, modifier = Modifier.clearAndSetSemantics {})
                    Text(connection.broker, style = NidooTheme.type.label, color = c.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Group(label: String, entries: List<Entry>, onOpen: (Entry) -> Unit) {
    val c = NidooTheme.colors
    val shape = RoundedCornerShape(NidooDimens.cardRadius)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = NidooTheme.type.label, color = c.contentMuted, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.cardBorder, shape)) {
            entries.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = c.cardBorder)
                SettingsRow(entry) { onOpen(entry) }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: Entry, onClick: () -> Unit) {
    val c = NidooTheme.colors
    val tone = entry.tone.color()
    Row(
        Modifier.fillMaxWidth().heightIn(min = maxOf(64.dp, NidooDimens.minTouch)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides tone) {
                StaticIcon(entry.glyph, accent = tone, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(entry.title), style = NidooTheme.type.body)
            Text(stringResource(entry.hint), style = NidooTheme.type.label, color = c.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val status = entry.status()
        if (status.isNotEmpty()) Text(status, style = NidooTheme.type.label, color = c.contentMuted, maxLines = 1)
        Text("›", style = NidooTheme.type.cardValue, color = c.contentMuted, modifier = Modifier.clearAndSetSemantics {})
    }
}

