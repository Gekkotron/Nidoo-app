package com.gekkotron.nidoo.di

import com.gekkotron.nidoo.feature.energy.history.EnergyHistoryImpl
import com.gekkotron.nidoo.history.EnergyHistory
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.source.EntitySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt's `EnergyHistory` for the History screen until it moves to Koin; Energy itself binds its own in `energyModule`. */
@Module
@InstallIn(SingletonComponent::class)
object EnergyHistoryHiltModule {
    @Provides
    fun energyHistory(source: EntitySource, history: HistoryClient): EnergyHistory = EnergyHistoryImpl(source, history)
}
