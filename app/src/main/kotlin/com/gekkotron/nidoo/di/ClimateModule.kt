package com.gekkotron.nidoo.di

import android.util.Log
import com.gekkotron.nidoo.climate.ClimateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClimateModule {
    /** The hub's Climat zones, fed by the main session's MQTT source (retained `nidoo/climate/zone/<zone>/<doc>`). */
    @Provides
    @Singleton
    fun climateRepository(): ClimateRepository = ClimateRepository(log = { Log.w("NidooClimate", it) })
}
