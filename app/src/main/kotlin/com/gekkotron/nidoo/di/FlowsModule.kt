package com.gekkotron.nidoo.di

import android.util.Log
import com.gekkotron.nidoo.alerts.media.MediaRoutes
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.effectiveHistoryUrl
import com.gekkotron.nidoo.data.isLikelyLocalHost
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.flows.HttpFlowsClient
import com.gekkotron.nidoo.transport.LanOrGatewayGet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FlowsModule {
    /**
     * The hub's flows: toggles go to the same host as NidooHistory (the profile's history URL), over the LAN or
     * the gateway. The demo and no profile have no hub, so the client fails without a request.
     */
    @Provides
    @Singleton
    fun flowsRepository(profiles: ProfileRepository, routes: MediaRoutes, http: OkHttpClient, @ApplicationScope scope: CoroutineScope): FlowsRepository {
        val lanOrGateway = LanOrGatewayGet(routes, http, ::isLikelyLocalHost, log = { Log.w("NidooFlows", it) })
        val client = HttpFlowsClient(
            baseUrl = {
                val ready = profiles.state.first { it !is ProfileState.Loading } as? ProfileState.Ready
                (ready?.profile as? ConnectionProfile.Direct)?.effectiveHistoryUrl()
            },
            post = { url, json -> lanOrGateway.post(url, json) },
        )
        return FlowsRepository(client, scope, log = { Log.w("NidooFlows", it) })
    }
}
