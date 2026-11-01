package com.gekkotron.nidoo.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.gekkotron.nidoo.designsystem.components.ChipItem
import com.gekkotron.nidoo.designsystem.components.NidooCard
import com.gekkotron.nidoo.designsystem.components.PillTone
import com.gekkotron.nidoo.designsystem.components.RoomChips
import com.gekkotron.nidoo.designsystem.components.StatusPill
import com.gekkotron.nidoo.designsystem.icons.DoorIcon
import com.gekkotron.nidoo.designsystem.icons.GarageIcon
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.ShutterIcon
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.designsystem.icons.WindowIcon

@Composable
private fun Swatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(28.dp).background(color, RoundedCornerShape(6.dp)))
        Text(label, style = NidooTheme.type.display.copy(fontSize = 9.sp), color = NidooTheme.colors.contentMuted)
    }
}

@Composable
private fun ThemeSheet(choice: ThemeChoice) = NidooTheme(choice) {
    val c = NidooTheme.colors
    NidooBackground(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Maison", style = NidooTheme.type.display, color = c.content)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NidooCard(Modifier.weight(1f)) {
                    Text("Salon", color = c.content)
                    Text("Off", color = c.contentMuted)
                }
                NidooCard(Modifier.weight(1f), on = true) {
                    Text("Cuisine", color = c.contentOnCardOn)
                    Text("On, 62%", color = c.contentOnCardOn)
                }
            }
            RoomChips(
                items = listOf(ChipItem("all", "All"), ChipItem("living", "Living"), ChipItem("kitchen", "Kitchen")),
                selectedKey = "living",
                onSelect = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Connected", PillTone.CONNECTED)
                StatusPill("Reconnecting", PillTone.WARNING)
                StatusPill("Offline", PillTone.OFFLINE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(c.solar, "solar")
                Swatch(c.water, "water")
                Swatch(c.grid, "grid")
                Swatch(c.heat, "heat")
                Swatch(c.connected, "ok")
                Swatch(c.warning, "warn")
                Swatch(c.offline, "off")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.background(c.connectedContainer, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("connected", color = c.connected)
                }
                Box(Modifier.background(c.warningContainer, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("warning", color = c.warning)
                }
                Box(Modifier.background(c.offlineContainer, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("offline", color = c.offline)
                }
            }
        }
    }
}

@Composable
private fun IconSheet(choice: ThemeChoice) = NidooTheme(choice) {
    val c = NidooTheme.colors
    NidooBackground(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (t in listOf(0f, 0.5f, 1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val accent = if (t > 0f) c.warning else c.connected
                    ShutterIcon(t, Modifier.size(40.dp))
                    DoorIcon(t, accent, Modifier.size(40.dp))
                    WindowIcon(t, accent, Modifier.size(40.dp))
                    GarageIcon(t, accent, Modifier.size(40.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NidooGlyph.entries.forEach { StaticIcon(it, if (it == NidooGlyph.BULB) c.warning else null) }
            }
        }
    }
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun NightGlassSheet() = ThemeSheet(ThemeChoice.NIGHT_GLASS)

@PreviewTest
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun IvoireSheet() = ThemeSheet(ThemeChoice.IVOIRE)

@PreviewTest
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun NightGlassIcons() = IconSheet(ThemeChoice.NIGHT_GLASS)

@PreviewTest
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun IvoireIcons() = IconSheet(ThemeChoice.IVOIRE)
