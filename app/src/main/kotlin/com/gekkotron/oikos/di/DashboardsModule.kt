package com.gekkotron.oikos.di

import android.util.Log
import com.gekkotron.oikos.dashboards.DashboardsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DashboardsModule {
    /** The hub's dashboards, fed by the main session's MQTT source (retained `nidoo/ui/dashboards/<id>`). */
    @Provides
    @Singleton
    fun dashboardsRepository(@ApplicationScope scope: CoroutineScope): DashboardsRepository =
        DashboardsRepository(scope, log = { Log.w("OikosDashboards", it) })
}
