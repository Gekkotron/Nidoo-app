package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.runtime.Composable
import com.gekkotron.nidoo.feature.energy.presentation.EnergyFormat
import com.gekkotron.nidoo.feature.energy.presentation.EnergyNode
import com.gekkotron.nidoo.feature.energy.presentation.EnergyUiState
import com.gekkotron.nidoo.feature.energy.presentation.Flow
import com.gekkotron.nidoo.feature.energy.presentation.NodeKind
import com.gekkotron.nidoo.format.FormatLocale
import com.gekkotron.nidoo.feature.energy.resources.Res
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_battery_in
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_battery_out
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_consumer
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_grid_in
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_grid_out
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_house
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_solar
import com.gekkotron.nidoo.feature.energy.resources.energy_cd_water
import com.gekkotron.nidoo.feature.energy.resources.energy_spoken_kw
import com.gekkotron.nidoo.feature.energy.resources.energy_spoken_w
import org.jetbrains.compose.resources.stringResource

/** The diagram as one TalkBack sentence (spec §5); unknown values are left out. */
@Composable
internal fun energyDescription(state: EnergyUiState, locale: FormatLocale): String {
    @Composable
    fun spoken(watts: Double): String {
        val number = EnergyFormat.spokenNumber(watts, locale)
        return if (watts >= 1_000) stringResource(Res.string.energy_spoken_kw, number) else stringResource(Res.string.energy_spoken_w, number)
    }

    @Composable
    fun part(n: EnergyNode): String? {
        val v = n.value ?: return null
        return when (n.kind) {
            NodeKind.SOLAR -> stringResource(Res.string.energy_cd_solar, spoken(v))
            NodeKind.HOUSE -> stringResource(Res.string.energy_cd_house, spoken(v))
            NodeKind.GRID -> when (n.flow) {
                Flow.IN -> stringResource(Res.string.energy_cd_grid_in, spoken(v))
                Flow.OUT -> stringResource(Res.string.energy_cd_grid_out, spoken(v))
                Flow.IDLE -> null
            }
            NodeKind.BATTERY -> when (n.flow) {
                Flow.IN -> stringResource(Res.string.energy_cd_battery_in, spoken(v))
                Flow.OUT -> stringResource(Res.string.energy_cd_battery_out, spoken(v))
                Flow.IDLE -> null
            }
            NodeKind.CONSUMER -> stringResource(Res.string.energy_cd_consumer, n.name.orEmpty(), spoken(v))
            NodeKind.WATER -> stringResource(Res.string.energy_cd_water, EnergyFormat.amount(v, n.unit, locale))
            NodeKind.MORE -> null
        }
    }
    // Every consumer is read, including the folded ones, so TalkBack users need no sheet.
    return (state.diagram.filter { it.kind != NodeKind.CONSUMER && it.kind != NodeKind.MORE && it.kind != NodeKind.WATER } +
        state.consumers + listOfNotNull(state.cards.firstOrNull { it.kind == NodeKind.WATER }))
        .mapNotNull { part(it) }
        .joinToString(" ")
}
