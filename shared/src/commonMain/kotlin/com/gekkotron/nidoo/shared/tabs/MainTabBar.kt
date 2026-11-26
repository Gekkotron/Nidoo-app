package com.gekkotron.nidoo.shared.tabs

import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.designsystem.icons.StaticIcon
import com.gekkotron.nidoo.shared.resources.Res
import com.gekkotron.nidoo.shared.resources.nav_automations
import com.gekkotron.nidoo.shared.resources.nav_energy
import com.gekkotron.nidoo.shared.resources.nav_home
import com.gekkotron.nidoo.shared.resources.nav_security
import org.jetbrains.compose.resources.stringResource

/** One item per entry of [tabs], in order; [selected] is null when the top screen is not a tab. */
@Composable
fun MainTabBar(
    tabs: List<MainTab>,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    status: TabStatus = TabStatus(),
) {
    val colors = NidooTheme.colors
    val itemColors = NavigationBarItemDefaults.colors(
        // The icon sits on the indicator pill; the label sits below it, on the bar.
        selectedIconColor = colors.chipSelectedContent,
        selectedTextColor = colors.content,
        unselectedIconColor = colors.contentMuted,
        unselectedTextColor = colors.contentMuted,
        indicatorColor = colors.chipSelected,
    )
    // When unselected and active, the glyph fills with its state colour; selected keeps the pill contrast.
    val bolt = if (!status.solarActive || selected == MainTab.ENERGY) null else colors.solar
    val shield = if (!status.alarmArmed || selected == MainTab.SECURITY) null else colors.warning
    NavigationBar(containerColor = colors.backgroundTop.copy(alpha = 0.92f)) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    when (tab) {
                        MainTab.HOME -> StaticIcon(NidooGlyph.HOUSE, modifier = Modifier.size(24.dp))
                        MainTab.ENERGY -> StaticIcon(NidooGlyph.BOLT, accent = bolt, modifier = Modifier.size(24.dp))
                        MainTab.SECURITY -> StaticIcon(NidooGlyph.SHIELD, accent = shield, modifier = Modifier.size(24.dp))
                        MainTab.AUTOMATIONS -> StaticIcon(NidooGlyph.AUTOMATION, modifier = Modifier.size(24.dp))
                    }
                },
                label = {
                    val text = when (tab) {
                        MainTab.HOME -> stringResource(Res.string.nav_home)
                        MainTab.ENERGY -> stringResource(Res.string.nav_energy)
                        MainTab.SECURITY -> stringResource(Res.string.nav_security)
                        MainTab.AUTOMATIONS -> stringResource(Res.string.nav_automations)
                    }
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = itemColors,
            )
        }
    }
}
