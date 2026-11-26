package com.gekkotron.nidoo.feature.settings.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** What a platform row's status column says. A plain enum so the view-model and its tests need no resources. */
enum class RowStatus { NONE, ON, OFF, PUSH_REGISTERED, PUSH_WAITING, PUSH_FAILED, PUSH_OFF }

/**
 * One extra House row a platform adds to the Settings list (push, presence and voice on Android). Only NOTIFICATIONS,
 * PRESENCE and VOICE are platform pages; `SettingsList`'s `platformEntry` rejects the others with error().
 */
data class PlatformRow(val page: SettingsPage, val status: RowStatus)

/** The Settings rows only one platform has. The common view-model reads them here instead of knowing push, presence or voice. */
interface PlatformSettings {
    val rows: Flow<List<PlatformRow>>

    /** The rows as they are now, to seed the state without a first-frame flash. */
    fun currentRows(): List<PlatformRow>

    /** The list was shown again: re-read anything the system may have changed while the app was away. */
    fun refresh()
}

/** No platform rows (iOS). */
object NoPlatformSettings : PlatformSettings {
    override val rows: Flow<List<PlatformRow>> = flowOf(emptyList())
    override fun currentRows(): List<PlatformRow> = emptyList()
    override fun refresh() = Unit
}
