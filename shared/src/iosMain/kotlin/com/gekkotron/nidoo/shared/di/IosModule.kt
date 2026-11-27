package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.DirectConnectionTester
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.connection.ProfileHistoryClient
import com.gekkotron.nidoo.connection.ProfileHttpRoutes
import com.gekkotron.nidoo.connection.SharedSubscriptions
import com.gekkotron.nidoo.connection.TransportFactory
import com.gekkotron.nidoo.connection.profileSpecs
import com.gekkotron.nidoo.connection.setupClientIdFor
import com.gekkotron.nidoo.dashboards.DashboardsRepository
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.EntityCache
import com.gekkotron.nidoo.data.FileEntityCache
import com.gekkotron.nidoo.data.KeychainSecretStore
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.effectiveHistoryUrl
import com.gekkotron.nidoo.data.isLikelyLocalHost
import com.gekkotron.nidoo.data.nidooCachePath
import com.gekkotron.nidoo.data.nidooDataPath
import com.gekkotron.nidoo.data.nidooDeviceOnlyPath
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.flows.HttpFlowsClient
import com.gekkotron.nidoo.assist.AssistClient
import com.gekkotron.nidoo.assist.HttpAssistClient
import com.gekkotron.nidoo.feature.settings.presentation.PlatformSettings
import com.gekkotron.nidoo.feature.settings.presentation.VoicePlatformSettings
import com.gekkotron.nidoo.feature.voice.AudioPlayer
import com.gekkotron.nidoo.feature.voice.IosAudioPlayer
import com.gekkotron.nidoo.feature.voice.IosSpeechCapture
import com.gekkotron.nidoo.feature.voice.IosVoicePermissions
import com.gekkotron.nidoo.feature.voice.SpeechAvailability
import com.gekkotron.nidoo.feature.voice.SpeechCaptureFactory
import com.gekkotron.nidoo.feature.voice.VoicePermissions
import com.gekkotron.nidoo.feature.voice.iosSpeechAvailability
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.shared.iosLog
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.AthenaGatewayApi
import com.gekkotron.nidoo.transport.AthenaGatewayDownloader
import com.gekkotron.nidoo.transport.DirectLink
import com.gekkotron.nidoo.transport.GatewayApi
import com.gekkotron.nidoo.transport.HttpRoutes
import com.gekkotron.nidoo.transport.KtorLanOrGatewayGet
import com.gekkotron.nidoo.transport.MqttClientPort
import com.gekkotron.nidoo.transport.MqttTransport
import com.gekkotron.nidoo.transport.NetworkTcpProbe
import com.gekkotron.nidoo.transport.PortMqttTransport
import com.gekkotron.nidoo.transport.TransportSpec
import com.gekkotron.nidoo.transport.iosForeground
import com.gekkotron.nidoo.transport.lanHttpClients
import com.gekkotron.nidoo.transport.iosNetworkChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * The iOS graph (Android builds the same with Hilt): stores in Application Support (the profile kept off backups, its
 * secrets in the Keychain), the transport stack with iOS links, and the app lifecycle feeding the transport.
 * [mqttPortFactory] makes one CocoaMQTT client per direct link (spec §9, MQTT gate).
 */
fun iosModule(mqttPortFactory: () -> MqttClientPort): Module = module {
    val direct: (TransportSpec.Direct, CoroutineScope) -> DirectLink = { spec, scope -> PortMqttTransport(spec, scope, mqttPortFactory()) }
    val gatewayApi: (TransportSpec.Gateway) -> GatewayApi = { AthenaGatewayApi(it) }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<SecretStore> { KeychainSecretStore() }
    single { ProfileRepository.create(nidooDeviceOnlyPath(ProfileRepository.FILE), get(), get()) }
    single { AppPreferences.create(nidooDataPath(AppPreferences.FILE), get()) }
    single<EntityCache> { FileEntityCache.create(nidooCachePath(FileEntityCache.FILE), get()) }
    single { TransportFactory(get(), iosNetworkChanges(), direct, gatewayApi, probe = { host, port -> NetworkTcpProbe(host, port) }) }
    single {
        val scope: CoroutineScope = get()
        val factory: TransportFactory = get()
        ActiveTransport(profileSpecs(get()), factory::create, scope).also { active ->
            // Connected only in the foreground; ActiveTransport pauses 30 s after the app leaves it (spec §5).
            scope.launch { iosForeground().collect(active::setForeground) }
        }
    }
    single { SharedSubscriptions(get<ActiveTransport>()) }
    single<MqttTransport> { get<SharedSubscriptions>() }
    single { ClimateRepository(get(), log = { iosLog("NidooClimate", it) }) }
    single { DashboardsRepository(get(), log = { iosLog("NidooDashboards", it) }) }
    single<HttpRoutes> { ProfileHttpRoutes(get(), local = { h, p -> NetworkTcpProbe(h, p) }, gateway = { AthenaGatewayDownloader(it) }) }
    // One LAN-or-gateway client for history and flows (Android builds one per module, with its own log tag).
    single { KtorLanOrGatewayGet(get(), lanHttpClients(), ::isLikelyLocalHost, log = { iosLog("NidooHttp", it) }) }
    single<HistoryClient> {
        val lanOrGateway: KtorLanOrGatewayGet = get()
        // History answers are small (1 MiB, as on Android); anything bigger is not ours.
        ProfileHistoryClient(get<ProfileRepository>().state, get = { lanOrGateway.get(it, 1L shl 20) }, scope = get())
    }
    // Toggles and voice commands go to the same host as the hub's history API; the demo and no profile have no hub.
    fun Scope.hubBaseUrl(): suspend () -> String? {
        val profiles: ProfileRepository = get()
        return {
            val ready = profiles.state.first { it !is ProfileState.Loading } as? ProfileState.Ready
            (ready?.profile as? ConnectionProfile.Direct)?.effectiveHistoryUrl()
        }
    }
    single {
        val lanOrGateway: KtorLanOrGatewayGet = get()
        val client = HttpFlowsClient(
            baseUrl = hubBaseUrl(),
            post = { url, json -> lanOrGateway.post(url, json) },
        )
        FlowsRepository(client, get(), log = { iosLog("NidooFlows", it) })
    }
    single {
        EntitySourceSession(
            get(), get(),
            diagnostics = { iosLog("Nidoo", "${it.source}: ${it.message}") },
            cache = get(),
            flowsSink = get<FlowsRepository>(),
            dashboardsSink = get<DashboardsRepository>(),
            climateSink = get<ClimateRepository>(),
        )
    }
    // Unscoped, as on Android: each screen gets the source for the profile saved right now.
    factory<EntitySource> { get<EntitySourceSession>().current((get<ProfileRepository>().state.value as? ProfileState.Ready)?.profile) }
    single<AssistClient> {
        val lanOrGateway: KtorLanOrGatewayGet = get()
        HttpAssistClient(
            baseUrl = hubBaseUrl(),
            clientId = get<AppPreferences>()::assistClientId,
            // A command is not sent twice: a gateway refusal or timeout is shown, never retried behind the user's back.
            post = { url, json -> lanOrGateway.post(url, json, resendViaGateway = false) },
            get = { url, max -> lanOrGateway.get(url, max) },
        )
    }
    single<AudioPlayer> { IosAudioPlayer() }
    single<SpeechCaptureFactory> { { IosSpeechCapture() } }
    single<VoicePermissions> { IosVoicePermissions }
    factory<SpeechAvailability.Status> { iosSpeechAvailability() }
    // No push or presence on iOS (spec §3): Settings lists Voice only.
    single<PlatformSettings> { VoicePlatformSettings(get(), get()) }
    single<ConnectionTester> {
        val profiles: ProfileRepository = get()
        DirectConnectionTester(clientId = { setupClientIdFor(profiles.installId()) }, direct = direct, gatewayApi = gatewayApi)
    }
}
