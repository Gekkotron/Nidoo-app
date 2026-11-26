package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.dashboards.Dashboard
import com.gekkotron.nidoo.dashboards.DashboardsSnapshot
import com.gekkotron.nidoo.dashboards.HOUSE_DASHBOARD_ID
import com.gekkotron.nidoo.data.DashboardChoice

data class Resolution(val view: ResolvedView, val switcherVisible: Boolean, val resetToHouse: Boolean)

sealed interface ResolvedView {
    data object House : ResolvedView
    data class Loading(val name: String?) : ResolvedView
    data class Shown(val dashboard: Dashboard) : ResolvedView
}

object DashboardSelection {
    /**
     * Which view the stored [choice] means. House users must never see a waiting line, so an unread
     * preference (null) is House. A stored dashboard the hub has not sent yet is Loading until the
     * snapshot settles, then falls back to House. The caller resets the stored choice unless the dashboard was only
     * dropped by the reconnect confirm window (it may come back).
     */
    fun resolve(choice: DashboardChoice?, snapshot: DashboardsSnapshot): Resolution {
        val any = snapshot.dashboards.isNotEmpty()
        if (choice == null || choice.id == HOUSE_DASHBOARD_ID) return Resolution(ResolvedView.House, any, false)
        val found = snapshot.dashboards.firstOrNull { it.id == choice.id }
        return when {
            found != null -> Resolution(ResolvedView.Shown(found), true, false)
            !snapshot.isSettled -> Resolution(ResolvedView.Loading(choice.name), true, false)
            // A dashboard the reconnect window dropped may still exist: show House but keep the choice.
            else -> Resolution(ResolvedView.House, any, choice.id !in snapshot.dropped)
        }
    }
}
