package com.gekkotron.nidoo.di

import android.content.Context
import android.util.Log
import com.gekkotron.nidoo.connection.androidConnectionTester
import com.gekkotron.nidoo.connection.androidTransportFactory
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.connection.SharedSubscriptions
import com.gekkotron.nidoo.connection.TransportFactory
import com.gekkotron.nidoo.connection.networkChanges
import com.gekkotron.nidoo.connection.setupClientIdFor
import com.gekkotron.nidoo.connection.profileSpecs
import com.gekkotron.nidoo.data.EntityCache
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.MqttTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun transportFactory(
        @ApplicationContext context: Context,
        http: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
    ): TransportFactory = androidTransportFactory(scope, context.networkChanges(), http)

    @Provides
    @Singleton
    fun activeTransport(
        profiles: ProfileRepository,
        factory: TransportFactory,
        @ApplicationScope scope: CoroutineScope,
    ): ActiveTransport = ActiveTransport(
        specs = profileSpecs(profiles),
        factory = factory::create,
        scope = scope,
    )

    @Provides
    @Singleton
    fun sharedSubscriptions(active: ActiveTransport): SharedSubscriptions = SharedSubscriptions(active)

    @Provides
    fun transport(shared: SharedSubscriptions): MqttTransport = shared

    @Provides
    @Singleton
    fun entitySourceSession(
        transport: MqttTransport,
        cache: EntityCache,
        flows: FlowsRepository,
        dashboards: DashboardsRepository,
        climate: ClimateRepository,
        @ApplicationScope scope: CoroutineScope,
    ): EntitySourceSession =
        EntitySourceSession(
            transport,
            scope,
            diagnostics = { Log.w("Nidoo", "${it.source}: ${it.message}") },
            cache = cache,
            flowsSink = flows,
            dashboardsSink = dashboards,
            climateSink = climate,
        )

    /** Unscoped: each new Home gets the source for the current profile (spec §2). Home only exists while Ready. */
    @Provides
    fun entitySource(session: EntitySourceSession, profiles: ProfileRepository): EntitySource =
        session.current((profiles.state.value as? ProfileState.Ready)?.profile)

    @Provides
    @Singleton
    fun connectionTester(profiles: ProfileRepository, http: OkHttpClient): ConnectionTester =
        androidConnectionTester(clientId = { setupClientIdFor(profiles.installId()) }, http)
}
