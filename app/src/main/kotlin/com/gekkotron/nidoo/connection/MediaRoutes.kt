package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.alerts.media.MediaRoute
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.RoutePreference
import com.gekkotron.nidoo.transport.AthenaGatewayDownloader
import com.gekkotron.nidoo.transport.TcpProbe
import okhttp3.OkHttpClient

/** How alert media can be fetched for [profile] under the user's route choice (push alerts spec §6.3). */
fun mediaRouteFor(profile: ConnectionProfile?, route: RoutePreference, http: OkHttpClient): MediaRoute =
    httpRouteFor(profile, route, ::TcpProbe) { AthenaGatewayDownloader(it, http) }
