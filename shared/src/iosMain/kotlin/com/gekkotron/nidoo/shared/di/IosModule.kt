package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.DirectConnectionTester
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.connection.SharedSubscriptions
import com.gekkotron.nidoo.connection.TransportFactory
import com.gekkotron.nidoo.connection.profileSpecs
import com.gekkotron.nidoo.connection.setupClientIdFor
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.EntityCache
import com.gekkotron.nidoo.data.FileEntityCache
import com.gekkotron.nidoo.data.KeychainSecretStore
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.nidooCachePath
import com.gekkotron.nidoo.data.nidooDataPath
import com.gekkotron.nidoo.data.nidooDeviceOnlyPath
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.AthenaGatewayApi
import com.gekkotron.nidoo.transport.DirectLink
import com.gekkotron.nidoo.transport.GatewayApi
import com.gekkotron.nidoo.transport.MqttClientPort
import com.gekkotron.nidoo.transport.MqttTransport
import com.gekkotron.nidoo.transport.NetworkTcpProbe
import com.gekkotron.nidoo.transport.PortMqttTransport
import com.gekkotron.nidoo.transport.TransportSpec
import com.gekkotron.nidoo.transport.iosForeground
import com.gekkotron.nidoo.transport.iosNetworkChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
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
    single { EntitySourceSession(get(), get(), cache = get()) }
    // Unscoped, as on Android: each screen gets the source for the profile saved right now.
    factory<EntitySource> { get<EntitySourceSession>().current((get<ProfileRepository>().state.value as? ProfileState.Ready)?.profile) }
    single<ConnectionTester> {
        val profiles: ProfileRepository = get()
        DirectConnectionTester(clientId = { setupClientIdFor(profiles.installId()) }, direct = direct, gatewayApi = gatewayApi)
    }
}
