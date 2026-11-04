package com.gekkotron.nidoo.di

import android.util.Log
import com.gekkotron.nidoo.alerts.media.MediaRoutes
import com.gekkotron.nidoo.assist.AssistClient
import com.gekkotron.nidoo.assist.HttpAssistClient
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.effectiveHistoryUrl
import com.gekkotron.nidoo.data.isLikelyLocalHost
import com.gekkotron.nidoo.transport.LanOrGatewayGet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistModule {
    /**
     * The hub's assistant, on the same host as its history and flows (the profile's history URL), over the LAN or the
     * gateway. The demo and no profile have no hub: the client answers NoHub without a request.
     */
    @Provides
    @Singleton
    fun assistClient(profiles: ProfileRepository, preferences: AppPreferences, routes: MediaRoutes, http: OkHttpClient): AssistClient {
        val lanOrGateway = LanOrGatewayGet(routes, http, ::isLikelyLocalHost, log = { Log.w("NidooAssist", it) })
        return HttpAssistClient(
            baseUrl = {
                val ready = profiles.state.first { it !is ProfileState.Loading } as? ProfileState.Ready
                (ready?.profile as? ConnectionProfile.Direct)?.effectiveHistoryUrl()
            },
            clientId = preferences::assistClientId,
            // A sentence or a tap must reach the hub once: never resend through the gateway after the LAN may have delivered it.
            post = { url, json -> lanOrGateway.post(url, json, resendViaGateway = false) },
            get = { url, maxBytes -> lanOrGateway.get(url, maxBytes) },
        )
    }
}
