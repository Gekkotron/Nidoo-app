package com.gekkotron.nidoo.di

import android.content.Context
import androidx.work.WorkManager
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.connection.HeadlessEntities
import com.gekkotron.nidoo.connection.TransportFactory
import com.gekkotron.nidoo.connection.VoiceWidgetSync
import com.gekkotron.nidoo.connection.WidgetMirror
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.EntityCache
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.feature.widget.WidgetComponent
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.feature.widget.WidgetRefresher
import com.gekkotron.nidoo.feature.widget.WidgetUpdater
import com.gekkotron.nidoo.feature.widget.store.FileWidgetConfigStore
import com.gekkotron.nidoo.feature.widget.store.FileWidgetSnapshotStore
import com.gekkotron.nidoo.feature.widget.store.WidgetConfigStore
import com.gekkotron.nidoo.feature.widget.store.WidgetSnapshotStore
import com.gekkotron.nidoo.feature.widget.ui.updateAllNidooWidgets
import com.gekkotron.nidoo.feature.widget.voice.updateAllVoiceWidgets
import com.gekkotron.nidoo.feature.widget.work.WidgetScheduler
import com.gekkotron.nidoo.feature.widget.work.WorkManagerWidgetScheduler
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.LinkState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** The widget's stores, apart from [WidgetModule] so a test can give each run its own files (one DataStore per file per process). */
@Module
@InstallIn(SingletonComponent::class)
object WidgetStoreModule {
    @Provides
    @Singleton
    fun widgetConfigs(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): WidgetConfigStore =
        FileWidgetConfigStore.create(context, scope)

    @Provides
    @Singleton
    fun widgetSnapshots(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): WidgetSnapshotStore =
        FileWidgetSnapshotStore.create(context, scope)
}

/** The home-screen widget (widgets spec §3). */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {
    @Provides
    @Singleton
    fun widgetScheduler(@ApplicationContext context: Context): WidgetScheduler = WorkManagerWidgetScheduler { WorkManager.getInstance(context) }

    @Provides
    @Singleton
    fun widgetUpdater(@ApplicationContext context: Context): WidgetUpdater = WidgetUpdater { updateAllNidooWidgets(context) }

    @Provides
    @Singleton
    fun widgetEntities(
        profiles: ProfileRepository,
        session: EntitySourceSession,
        active: ActiveTransport,
        cache: EntityCache,
        http: OkHttpClient,
    ): WidgetEntities = HeadlessEntities(
        profile = { profiles.state.first { it !is ProfileState.Loading } },
        route = { profiles.route.value },
        installId = { profiles.installId() },
        live = { if (active.link.value is LinkState.Connected) session.peek() else null },
        foreground = { active.link.value is LinkState.Connected },
        cache = cache,
        // A session is too short to care about network switches.
        transports = { spec, scope -> TransportFactory(scope, emptyFlow(), http).create(spec) },
    )

    @Provides
    @Singleton
    fun widgetRefresher(entities: WidgetEntities, configs: WidgetConfigStore, snapshots: WidgetSnapshotStore, updater: WidgetUpdater): WidgetRefresher =
        WidgetRefresher(entities, configs, snapshots, updater)

    @Provides
    @Singleton
    fun widgetComponent(
        entities: WidgetEntities,
        configs: WidgetConfigStore,
        snapshots: WidgetSnapshotStore,
        scheduler: WidgetScheduler,
        refresher: WidgetRefresher,
        preferences: AppPreferences,
    ): WidgetComponent = WidgetComponent(entities, configs, snapshots, scheduler, refresher, preferences.voiceEnabled.map { it ?: false })

    @Provides
    @Singleton
    fun widgetMirror(
        profiles: ProfileRepository,
        session: EntitySourceSession,
        active: ActiveTransport,
        configs: WidgetConfigStore,
        snapshots: WidgetSnapshotStore,
        updater: WidgetUpdater,
        @ApplicationScope scope: CoroutineScope,
    ): WidgetMirror = WidgetMirror(
        sources = active.link.map { if (it is LinkState.Connected) session.peek() else null }.distinctUntilChanged(),
        configs = configs,
        snapshots = snapshots,
        updater = updater,
        historyEnabled = { (profiles.state.value as? ProfileState.Ready)?.profile is ConnectionProfile.Direct },
        scope = scope,
    )

    @Provides
    @Singleton
    fun voiceWidgetSync(
        @ApplicationContext context: Context,
        preferences: AppPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): VoiceWidgetSync = VoiceWidgetSync(
        voiceEnabled = preferences.voiceEnabled,
        update = {
            updateAllNidooWidgets(context)
            updateAllVoiceWidgets(context)
        },
        scope = scope,
    )
}
