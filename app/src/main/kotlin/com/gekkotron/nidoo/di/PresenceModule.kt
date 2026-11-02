package com.gekkotron.nidoo.di

import android.content.Context
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.presence.CurrentLocationSource
import com.gekkotron.nidoo.presence.FusedCurrentLocationSource
import com.gekkotron.nidoo.presence.PresenceGeofenceManager
import com.gekkotron.nidoo.presence.PresenceWorkerFactory
import com.gekkotron.nidoo.presence.WorkManagerPresenceRefresher
import com.gekkotron.nidoo.push.PresenceRefresher
import com.gekkotron.nidoo.push.TopicPublisher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PresenceModule {
    @Provides
    @Singleton
    fun currentLocationSource(@ApplicationContext context: Context): CurrentLocationSource = FusedCurrentLocationSource(context)

    @Provides
    @Singleton
    fun presenceWorkerFactory(
        publisher: TopicPublisher,
        preferences: AppPreferences,
        fixSource: CurrentLocationSource,
    ): PresenceWorkerFactory = PresenceWorkerFactory(publisher, preferences, fixSource)

    @Provides
    @Singleton
    fun presenceGeofenceManager(
        @ApplicationContext context: Context,
        preferences: AppPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): PresenceGeofenceManager = PresenceGeofenceManager(context, preferences, scope)

    @Provides
    @Singleton
    fun presenceRefresher(@ApplicationContext context: Context): PresenceRefresher = WorkManagerPresenceRefresher(context)
}
