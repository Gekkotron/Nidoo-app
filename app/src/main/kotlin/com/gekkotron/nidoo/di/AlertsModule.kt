package com.gekkotron.nidoo.di

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import com.gekkotron.nidoo.DemoAlertSeeder
import com.gekkotron.nidoo.alerts.AlertRepository
import com.gekkotron.nidoo.alerts.db.AlertDatabase
import com.gekkotron.nidoo.alerts.media.AlertImageCache
import com.gekkotron.nidoo.alerts.media.AlertMediaFetcher
import com.gekkotron.nidoo.alerts.media.AlertMediaKeyer
import com.gekkotron.nidoo.alerts.media.FrigateAuth
import com.gekkotron.nidoo.alerts.media.MediaFetcher
import com.gekkotron.nidoo.alerts.media.MediaRoutes
import com.gekkotron.nidoo.alerts.media.MediaSource
import com.gekkotron.nidoo.alerts.media.VideoCache
import com.gekkotron.nidoo.alerts.sync.HistorySync
import com.gekkotron.nidoo.connection.HistoryWiring
import com.gekkotron.nidoo.connection.ProfileMediaRoutes
import com.gekkotron.nidoo.connection.SharedSubscriptions
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.isLikelyLocalHost
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.LanOrGatewayGet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlertsModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AlertDatabase = AlertDatabase.create(context)

    @Provides
    @Singleton
    fun repository(db: AlertDatabase): AlertRepository = AlertRepository(db.alerts())

    @Provides
    @Singleton
    fun demoAlertSeeder(profiles: ProfileRepository, repository: AlertRepository, @ApplicationScope scope: CoroutineScope): DemoAlertSeeder =
        DemoAlertSeeder(profiles.state, repository, scope = scope)

    @Provides
    @Singleton
    fun historySync(active: ActiveTransport, repository: AlertRepository): HistorySync = HistorySync(active.messages(), repository)

    @Provides
    @Singleton
    fun historyWiring(
        profiles: ProfileRepository,
        shared: SharedSubscriptions,
        sync: HistorySync,
        @ApplicationScope scope: CoroutineScope,
    ): HistoryWiring = HistoryWiring(profiles.state, shared, sync, scope)

    @Provides
    @Singleton
    fun mediaRoutes(profiles: ProfileRepository, http: OkHttpClient): MediaRoutes = ProfileMediaRoutes(profiles, http)

    @Provides
    @Singleton
    fun frigateAuth(profiles: ProfileRepository, routes: MediaRoutes, http: OkHttpClient): FrigateAuth {
        val lan = LanOrGatewayGet(routes, http, ::isLikelyLocalHost, log = { Log.w("NidooFrigate", it) })
        return FrigateAuth(
            config = { ((profiles.state.first { it !is ProfileState.Loading } as? ProfileState.Ready)?.profile as? ConnectionProfile.Direct)?.frigate },
            post = lan::post,
        )
    }

    @Provides
    @Singleton
    fun mediaFetcher(routes: MediaRoutes, http: OkHttpClient, auth: FrigateAuth): MediaFetcher = MediaFetcher(routes, http, auth = auth)

    @Provides
    @Singleton
    fun mediaSource(fetcher: MediaFetcher): MediaSource = fetcher

    @Provides
    @Singleton
    fun imageCache(@ApplicationContext context: Context): AlertImageCache = AlertImageCache.create(context)

    @Provides
    @Singleton
    fun videoCache(@ApplicationContext context: Context, source: MediaSource): VideoCache = VideoCache.create(context, source)

    /** One loader, the shared disk cache, and the fetcher for `AlertMedia` only (spec §6.3). */
    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context, source: MediaSource, cache: AlertImageCache): ImageLoader =
        alertImageLoader(context, source, cache)
}

/** The app's Coil loader: `AlertMedia` is keyed by URL and fetched through [source], both backed by [cache]'s disk cache. */
fun alertImageLoader(context: Context, source: MediaSource, cache: AlertImageCache): ImageLoader =
    ImageLoader.Builder(context)
        .diskCache(cache.diskCache)
        .components {
            add(AlertMediaKeyer)
            add(AlertMediaFetcher.Factory(source, cache))
        }
        .build()
