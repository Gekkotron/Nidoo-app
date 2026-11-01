package com.gekkotron.nidoo.di

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.gekkotron.nidoo.SetUpAgainCleanup
import com.gekkotron.nidoo.alerts.AlertRepository
import com.gekkotron.nidoo.alerts.media.AlertImageCache
import com.gekkotron.nidoo.alerts.media.MediaSource
import com.gekkotron.nidoo.alerts.media.VideoCache
import com.gekkotron.nidoo.connection.ProfilePushTargets
import com.gekkotron.nidoo.connection.PushWiring
import com.gekkotron.nidoo.connection.oneShotSpec
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.feature.widget.WidgetUpdater
import com.gekkotron.nidoo.feature.widget.store.WidgetSnapshotStore
import com.gekkotron.nidoo.feature.widget.store.resetForSetUp
import com.gekkotron.nidoo.push.AlertChannels
import com.gekkotron.nidoo.push.AlertIngestor
import com.gekkotron.nidoo.push.AlertNotifier
import com.gekkotron.nidoo.push.DefaultPushStatusSource
import com.gekkotron.nidoo.push.FcmTokens
import com.gekkotron.nidoo.push.FetchedNotificationImages
import com.gekkotron.nidoo.push.FirebaseFcmTokens
import com.gekkotron.nidoo.push.OneShotPublisher
import com.gekkotron.nidoo.push.PushComponent
import com.gekkotron.nidoo.push.PushRegistrar
import com.gekkotron.nidoo.push.PushStatusSource
import com.gekkotron.nidoo.push.PushTargets
import com.gekkotron.nidoo.push.PushWorkerFactory
import com.gekkotron.nidoo.push.RegistrationQueue
import com.gekkotron.nidoo.push.RegistrationStore
import com.gekkotron.nidoo.push.ReplyFailureNotifier
import com.gekkotron.nidoo.push.ReplyQueue
import com.gekkotron.nidoo.push.TopicPublisher
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.AthenaGatewayApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object PushModule {
    @Provides
    @Singleton
    fun pushTargets(profiles: ProfileRepository): PushTargets = ProfilePushTargets(profiles)

    @Provides
    @Singleton
    fun registrationStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): RegistrationStore =
        RegistrationStore.create(context, scope)

    @Provides
    @Singleton
    fun fcmTokens(@ApplicationContext context: Context): FcmTokens = FirebaseFcmTokens(context)

    @Provides
    @Singleton
    fun oneShotPublisher(active: ActiveTransport, profiles: ProfileRepository, http: OkHttpClient): OneShotPublisher =
        OneShotPublisher(live = active, specs = { profiles.oneShotSpec() }, gatewayApi = { AthenaGatewayApi(it, http) })

    @Provides
    fun topicPublisher(publisher: OneShotPublisher): TopicPublisher = publisher

    @Provides
    @Singleton
    fun registrar(
        targets: PushTargets,
        tokens: FcmTokens,
        publisher: TopicPublisher,
        store: RegistrationStore,
        queue: RegistrationQueue,
    ): PushRegistrar = PushRegistrar(
        targets, tokens, publisher, store, queue,
        device = { PushRegistrar.DeviceInfo(Build.MODEL, Locale.getDefault().toLanguageTag()) },
    )

    @Provides
    @Singleton
    fun setUpAgainCleanup(
        @ApplicationContext context: Context,
        registrar: PushRegistrar,
        repository: AlertRepository,
        replies: ReplyQueue,
        images: AlertImageCache,
        videos: VideoCache,
        widgetSnapshots: WidgetSnapshotStore,
        widgetUpdater: WidgetUpdater,
    ): SetUpAgainCleanup = SetUpAgainCleanup(
        registrar, repository, replies, images, videos,
        resetWidgets = {
            widgetSnapshots.resetForSetUp()
            widgetUpdater.updateAll()
        },
        dismissNotifications = { NotificationManagerCompat.from(context).cancelAll() },
    )

    @Provides
    @Singleton
    fun channels(@ApplicationContext context: Context): AlertChannels = AlertChannels(context)

    @Provides
    @Singleton
    fun notifier(@ApplicationContext context: Context, source: MediaSource, cache: AlertImageCache): AlertNotifier =
        AlertNotifier(context, FetchedNotificationImages(source, cache))

    @Provides
    @Singleton
    fun ingestor(repository: AlertRepository, notifier: AlertNotifier): AlertIngestor = AlertIngestor(repository, notifier)

    @Provides
    @Singleton
    fun replyFailures(@ApplicationContext context: Context): ReplyFailureNotifier = ReplyFailureNotifier(context)

    @Provides
    @Singleton
    fun pushComponent(
        alertIngestor: AlertIngestor,
        pushRegistrar: PushRegistrar,
        replyQueue: ReplyQueue,
        presenceRefresher: com.gekkotron.nidoo.push.PresenceRefresher,
    ): PushComponent = object : PushComponent {
        override val ingestor = alertIngestor
        override val registrar = pushRegistrar
        override val replies = replyQueue
        override val presenceRefresher = presenceRefresher
    }

    @Provides
    @Singleton
    fun workerFactory(registrar: PushRegistrar, publisher: TopicPublisher, targets: PushTargets, failures: ReplyFailureNotifier): PushWorkerFactory =
        PushWorkerFactory(registrar, publisher, targets, failures)

    @Provides
    @Singleton
    fun pushWiring(active: ActiveTransport, registrar: PushRegistrar, @ApplicationScope scope: CoroutineScope): PushWiring =
        PushWiring(active.link, { registrar.onLinkConnected() }, scope)

    @Provides
    @Singleton
    fun pushStatusSource(
        @ApplicationContext context: Context,
        targets: PushTargets,
        store: RegistrationStore,
        queue: RegistrationQueue,
        registrar: PushRegistrar,
        @ApplicationScope scope: CoroutineScope,
    ): PushStatusSource = DefaultPushStatusSource(
        targets, store, queue, registrar,
        notificationsEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
        scope = scope,
    )
}
