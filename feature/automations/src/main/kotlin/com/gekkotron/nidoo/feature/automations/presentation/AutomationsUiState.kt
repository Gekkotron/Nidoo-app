package com.gekkotron.nidoo.feature.automations.presentation

import androidx.annotation.StringRes
import com.gekkotron.nidoo.flows.WakeUpTime
import com.gekkotron.nidoo.model.UNASSIGNED_AREA

const val ALL_KEY = "__all__"

/** A filter chip; the screen maps [All] and [Unassigned] to localized labels. */
sealed interface AutomationChip {
    val key: String

    data object All : AutomationChip {
        override val key = ALL_KEY
    }

    data class Room(val id: String, val name: String) : AutomationChip {
        override val key get() = id
    }

    data object Unassigned : AutomationChip {
        override val key = UNASSIGNED_AREA
    }
}

sealed interface AreaLabel {
    data class Named(val name: String) : AreaLabel
    data object Unassigned : AreaLabel
}

/** [area] is set only under the All chip; [toggleEnabled] is false offline or while a request runs. */
data class AutomationRow(
    val id: String,
    val name: String,
    val area: AreaLabel?,
    val enabled: Boolean,
    val toggleEnabled: Boolean,
)

/** The two halves of the Routines tab. */
enum class RoutineSection { Flows, WakeUp }

/** A wake-up alarm; [time] is null when the hub's `wake_up` could not be read (the row is then not editable). */
data class AlarmRow(
    val id: String,
    val name: String,
    val time: WakeUpTime?,
    val enabled: Boolean,
    val toggleEnabled: Boolean,
)

/** What the edit sheet shows; a copy taken when the row was tapped. [days] is 0 = Sunday .. 6 = Saturday. */
data class AlarmEditor(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val saving: Boolean = false,
    @StringRes val error: Int? = null,
) {
    val canSave get() = days.isNotEmpty() && !saving
}

data class AutomationsUiState(
    val isLoading: Boolean,
    val isOffline: Boolean,
    val chips: List<AutomationChip>,
    val selectedChipKey: String,
    val rows: List<AutomationRow>,
    @StringRes val toast: Int?,
    val section: RoutineSection = RoutineSection.Flows,
    val alarms: List<AlarmRow> = emptyList(),
    val editor: AlarmEditor? = null,
)
