package com.gekkotron.nidoo.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The theme the user picked on this phone (settings spec §3). Mirrors `ThemeChoice` without depending on the design system. */
enum class ThemePreference { SYSTEM, NIGHT_GLASS, IVOIRE }

/** How the Energy tab draws the live flow on this phone (energy views spec). Independent of the hub's own choice. */
enum class EnergyViewPreference { CLASSIC, FLUX, ORBITE }

/** Phone-local presence settings. [radiusM] is clamped on save; a null home means no geofence can register yet. */
@Serializable
data class PresenceConfig(
    val enabled: Boolean = false,
    val alias: String = "",
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val radiusM: Int = 150,
) {
    companion object {
        const val MIN_RADIUS_M = 50
        const val MAX_RADIUS_M = 500
    }
}

/** The dashboard the user picked on this phone. [id] "house" is House; [name] is cached so a cold start can show its title before the hub replays it. */
data class DashboardChoice(val id: String, val name: String?)

@Serializable
internal data class StoredPresence(
    val enabled: Boolean = false,
    val alias: String = "",
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val radiusM: Int = 150,
)

/** The theme is kept as a name so a value written by a newer app decodes as System instead of corrupting the file. */
@Serializable
internal data class StoredPreferences(
    val theme: String = ThemePreference.SYSTEM.name,
    val voiceEnabled: Boolean = false,
    val presence: StoredPresence = StoredPresence(),
    // "house" must match HOUSE_DASHBOARD_ID in :core:dashboards (the hub refuses "house" as a dashboard id). Literal here so :core:data stays independent.
    val dashboardId: String = "house",
    val dashboardName: String? = null,
    val energyView: String = EnergyViewPreference.CLASSIC.name,
)

/**
 * Per-phone preferences, kept apart from the profile so "Set up again" leaves them alone.
 *
 * The flows are not fed by collecting `store.data`. DataStore (1.2.1 `DataStoreImpl.readDataAndUpdateCache`)
 * opens each collection with an unlocked read; if a write is between its version bump and its rename,
 * that read returns the old file tagged with the new version, and the collection then drops the write as
 * already seen until some later write. So the file is read once while no write can be in flight, and each
 * write publishes the value DataStore committed. [writes] orders both, so the flows only move forward.
 * This holds because this class is the file's only writer.
 */
class AppPreferences internal constructor(private val store: DataStore<StoredPreferences>, scope: CoroutineScope) {
    private val writes = Mutex()
    private var loaded = false // guarded by [writes]

    private val _theme = MutableStateFlow<ThemePreference?>(null)
    private val _voiceEnabled = MutableStateFlow<Boolean?>(null)
    private val _presence = MutableStateFlow<PresenceConfig?>(null)
    private val _selectedDashboard = MutableStateFlow<DashboardChoice?>(null)
    private val _energyView = MutableStateFlow<EnergyViewPreference?>(null)

    /** null until the file has been read. */
    val theme: StateFlow<ThemePreference?> = _theme.asStateFlow()

    /** null until the file has been read. */
    val voiceEnabled: StateFlow<Boolean?> = _voiceEnabled.asStateFlow()

    /** null until the file has been read. */
    val presence: StateFlow<PresenceConfig?> = _presence.asStateFlow()

    /** null until the file has been read. */
    val selectedDashboard: StateFlow<DashboardChoice?> = _selectedDashboard.asStateFlow()

    /** null until the file has been read. */
    val energyView: StateFlow<EnergyViewPreference?> = _energyView.asStateFlow()

    init {
        scope.launch {
            writes.withLock { if (!loaded) publish(store.data.first()) }
        }
    }

    suspend fun setTheme(theme: ThemePreference) = update { it.copy(theme = theme.name) }

    suspend fun setEnergyView(view: EnergyViewPreference) = update { it.copy(energyView = view.name) }

    suspend fun setVoiceEnabled(enabled: Boolean) = update { it.copy(voiceEnabled = enabled) }

    suspend fun setPresence(config: PresenceConfig) {
        val radius = config.radiusM.coerceIn(PresenceConfig.MIN_RADIUS_M, PresenceConfig.MAX_RADIUS_M)
        update {
            it.copy(
                presence = StoredPresence(
                    enabled = config.enabled,
                    alias = config.alias,
                    homeLat = config.homeLat,
                    homeLng = config.homeLng,
                    radiusM = radius,
                ),
            )
        }
    }

    suspend fun setSelectedDashboard(id: String, name: String?) = update { it.copy(dashboardId = id, dashboardName = name) }

    /** Writes the choice only while the stored id is still [expectedId], so a tap that raced a rename or reset is never overwritten. */
    suspend fun replaceSelectedDashboard(expectedId: String, id: String, name: String?) =
        update { if (it.dashboardId == expectedId) it.copy(dashboardId = id, dashboardName = name) else it }

    /** The flows show the write as soon as this returns. */
    private suspend fun update(transform: (StoredPreferences) -> StoredPreferences) {
        writes.withLock {
            // DataStore finishes a write whose caller was cancelled, so publish it regardless.
            withContext(NonCancellable) { publish(store.updateData(transform)) }
        }
    }

    private fun publish(stored: StoredPreferences) {
        loaded = true
        // WARM_MINIMAL was saved before Ivoire replaced Warm Minimal.
        val themeName = if (stored.theme == "WARM_MINIMAL") ThemePreference.IVOIRE.name else stored.theme
        _theme.value = ThemePreference.entries.firstOrNull { it.name == themeName } ?: ThemePreference.SYSTEM
        _voiceEnabled.value = stored.voiceEnabled
        val p = stored.presence
        _presence.value = PresenceConfig(p.enabled, p.alias, p.homeLat, p.homeLng, p.radiusM)
        _selectedDashboard.value = DashboardChoice(stored.dashboardId, stored.dashboardName)
        _energyView.value = EnergyViewPreference.entries.firstOrNull { it.name == stored.energyView } ?: EnergyViewPreference.CLASSIC
    }

    companion object {
        const val FILE = "datastore/app-preferences.json"

        fun create(context: Context, scope: CoroutineScope): AppPreferences = create(context.filesDir.resolve(FILE), scope)

        fun create(file: File, scope: CoroutineScope): AppPreferences {
            val store = DataStoreFactory.create(
                serializer = PreferencesSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { StoredPreferences() },
                scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
                produceFile = { file },
            )
            return AppPreferences(store, scope)
        }
    }
}

internal object PreferencesSerializer : Serializer<StoredPreferences> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: StoredPreferences = StoredPreferences()

    /** `SerializationException` is an `IllegalArgumentException`, so this covers malformed JSON too. */
    override suspend fun readFrom(input: InputStream): StoredPreferences = try {
        json.decodeFromString(StoredPreferences.serializer(), input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("unreadable preferences", e)
    }

    override suspend fun writeTo(t: StoredPreferences, output: OutputStream) {
        output.write(json.encodeToString(StoredPreferences.serializer(), t).encodeToByteArray())
    }
}
