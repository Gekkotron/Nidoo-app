package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.dashboards.Dashboard
import com.gekkotron.nidoo.dashboards.DashboardsSnapshot
import com.gekkotron.nidoo.data.DashboardChoice
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardSelectionTest {
    private val a = Dashboard("a", "A", 0, false, emptyList())
    private fun snap(settled: Boolean, vararg d: Dashboard) = DashboardsSnapshot(settled, d.toList())

    @Test
    fun `unread preference is House without a waiting line`() {
        assertEquals(Resolution(ResolvedView.House, true, false), DashboardSelection.resolve(null, snap(false, a)))
        assertEquals(Resolution(ResolvedView.House, false, false), DashboardSelection.resolve(null, snap(false)))
    }

    @Test
    fun `house choice is House and the switcher follows the dashboards`() {
        assertEquals(Resolution(ResolvedView.House, true, false), DashboardSelection.resolve(DashboardChoice("house", null), snap(true, a)))
    }

    @Test
    fun `switcher hidden with no dashboards on House`() {
        assertEquals(Resolution(ResolvedView.House, false, false), DashboardSelection.resolve(DashboardChoice("house", null), snap(true)))
    }

    @Test
    fun `a found id is shown`() {
        assertEquals(Resolution(ResolvedView.Shown(a), true, false), DashboardSelection.resolve(DashboardChoice("a", "A"), snap(false, a)))
    }

    @Test
    fun `a missing id waits while not settled`() {
        assertEquals(Resolution(ResolvedView.Loading("Old"), true, false), DashboardSelection.resolve(DashboardChoice("x", "Old"), snap(false)))
    }

    @Test
    fun `a missing id after settling falls back to House and resets`() {
        assertEquals(Resolution(ResolvedView.House, true, true), DashboardSelection.resolve(DashboardChoice("x", "Old"), snap(true, a)))
        assertEquals(Resolution(ResolvedView.House, false, true), DashboardSelection.resolve(DashboardChoice("x", "Old"), snap(true)))
    }

    @Test
    fun `missing and dropped - House without reset`() {
        val dropped = DashboardsSnapshot(true, listOf(a), dropped = setOf("x"))
        assertEquals(Resolution(ResolvedView.House, true, false), DashboardSelection.resolve(DashboardChoice("x", "Old"), dropped))
    }

    @Test
    fun `missing, settled, not dropped - House with reset`() {
        val other = DashboardsSnapshot(true, listOf(a), dropped = setOf("y"))
        assertEquals(Resolution(ResolvedView.House, true, true), DashboardSelection.resolve(DashboardChoice("x", "Old"), other))
    }
}
