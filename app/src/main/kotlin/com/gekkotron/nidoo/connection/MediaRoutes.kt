package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.alerts.media.MediaRoute
import com.gekkotron.nidoo.alerts.media.MediaRoutes
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.RoutePreference
import com.gekkotron.nidoo.transport.AthenaGatewayDownloader
import com.gekkotron.nidoo.transport.TcpProbe
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/** How alert media can be fetched for [profile] under the user's route choice (push alerts spec §6.3). */
fun mediaRouteFor(profile: ConnectionProfile?, route: RoutePreference, http: OkHttpClient): MediaRoute {
    val direct = profile as? ConnectionProfile.Direct ?: return MediaRoute(null, null)
    val local = if (route != RoutePreference.GATEWAY) TcpProbe(direct.local.host, direct.local.port) else null
    val gateway = if (route != RoutePreference.DIRECT) {
        direct.gateway?.let { AthenaGatewayDownloader(it.toSpec(direct.local), http) }
    } else {
        null
    }
    return MediaRoute(local, gateway)
}

class ProfileMediaRoutes(private val profiles: ProfileRepository, private val http: OkHttpClient) : MediaRoutes {
    override suspend fun current(): MediaRoute {
        val state = profiles.state.first { it !is ProfileState.Loading }
        return mediaRouteFor((state as? ProfileState.Ready)?.profile, profiles.route.value, http)
    }
}
