package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.RoutePreference
import com.gekkotron.nidoo.transport.GatewayDownloader
import com.gekkotron.nidoo.transport.HttpRoute
import com.gekkotron.nidoo.transport.HttpRoutes
import com.gekkotron.nidoo.transport.LocalProbe
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.flow.first

/** How a LAN URL can be reached for [profile] under the user's route choice (push alerts spec §6.3): a Direct profile only, the LAN unless the route is gateway-only, the gateway unless it is LAN-only. */
fun httpRouteFor(
    profile: ConnectionProfile?,
    route: RoutePreference,
    local: (host: String, port: Int) -> LocalProbe,
    gateway: (TransportSpec.Gateway) -> GatewayDownloader,
): HttpRoute {
    val direct = profile as? ConnectionProfile.Direct ?: return HttpRoute(null, null)
    val probe = if (route != RoutePreference.GATEWAY) local(direct.local.host, direct.local.port) else null
    val downloader = if (route != RoutePreference.DIRECT) direct.gateway?.let { gateway(it.toSpec(direct.local)) } else null
    return HttpRoute(probe, downloader)
}

/** The route for the stored profile and the user's route choice, read on every call. */
class ProfileHttpRoutes(
    private val profiles: ProfileRepository,
    private val local: (host: String, port: Int) -> LocalProbe,
    private val gateway: (TransportSpec.Gateway) -> GatewayDownloader,
) : HttpRoutes {
    override suspend fun current(): HttpRoute {
        val state = profiles.state.first { it !is ProfileState.Loading }
        return httpRouteFor((state as? ProfileState.Ready)?.profile, profiles.route.value, local, gateway)
    }
}
