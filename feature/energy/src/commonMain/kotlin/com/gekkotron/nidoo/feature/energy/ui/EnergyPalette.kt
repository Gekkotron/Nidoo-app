package com.gekkotron.nidoo.feature.energy.ui

import androidx.compose.ui.graphics.Color
import com.gekkotron.nidoo.designsystem.NidooColors
import com.gekkotron.nidoo.feature.energy.presentation.SankeyRole

/** The colour a Flux bar/band or Orbite arc is painted in (the hub's `colour_var`); roles keep Classic's colours. */
internal fun sankeyColor(role: SankeyRole, slot: Int, colors: NidooColors): Color = when (role) {
    SankeyRole.SOLAR -> colors.solar
    SankeyRole.GRID, SankeyRole.EXPORT -> colors.grid
    SankeyRole.BATTERY, SankeyRole.BATTERY_CHARGE -> colors.connected
    SankeyRole.CONSUMER -> colors.energyConsumers[slot.mod(colors.energyConsumers.size)]
    SankeyRole.HOUSE, SankeyRole.OTHERS, SankeyRole.UNITEMISED -> colors.energyMuted
}
