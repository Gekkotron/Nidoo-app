package com.gekkotron.nidoo.feature.energy.ui

import com.gekkotron.nidoo.designsystem.icons.NidooGlyph
import com.gekkotron.nidoo.model.ConsumerIcon

internal val ConsumerIcon.glyph: NidooGlyph
    get() = when (this) {
        ConsumerIcon.WATER_HEATER -> NidooGlyph.WATER_HEATER
        ConsumerIcon.HEAT_PUMP -> NidooGlyph.HEAT_PUMP
        ConsumerIcon.OVEN -> NidooGlyph.OVEN
        ConsumerIcon.FRIDGE -> NidooGlyph.FRIDGE
        ConsumerIcon.WASHER -> NidooGlyph.WASHER
        ConsumerIcon.DRYER -> NidooGlyph.DRYER
        ConsumerIcon.DISHWASHER -> NidooGlyph.DISHWASHER
    }
