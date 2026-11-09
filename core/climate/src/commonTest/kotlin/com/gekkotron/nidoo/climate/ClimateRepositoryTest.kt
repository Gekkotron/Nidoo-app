package com.gekkotron.nidoo.climate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateRepositoryTest {

    private val settings = """{"state":"active","entities":["ac"],"sensor":null}"""
    private val decision =
        """{"zone":"living","mode":"heat","setpoint":20.0,"source":"schedule","reason":"Schedule","reading":19.4,"at":10,"entities":{"ac":{}}}"""
    private val boost = """[{"entity":null,"setpoint":22.0,"mode":null,"until":100,"origin":"user"}]"""

    @Test
    fun collectsDocsPerZone() {
        val r = ClimateRepository()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        r.onRetained("living", ClimateDoc.OVERRIDE, boost)
        r.onRetained("bed", ClimateDoc.DECISION, decision)
        val living = r.zones.value.getValue("living")
        assertEquals("living", living.id)
        assertEquals("active", living.settings?.state)
        assertEquals(ClimateSetpoint.Temp(20.0), living.decision?.setpoint)
        assertEquals(1, living.overrides.size)
        val bed = r.zones.value.getValue("bed")
        assertNull(bed.settings)
        assertTrue(bed.overrides.isEmpty())
    }

    @Test
    fun emptySettingsRemovesZone() {
        val r = ClimateRepository()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        r.onRetained("living", ClimateDoc.SETTINGS, "")
        assertTrue(r.zones.value.isEmpty())
        r.onRetained("bed", ClimateDoc.SETTINGS, settings)
        r.onRetained("bed", ClimateDoc.SETTINGS, null)
        assertTrue(r.zones.value.isEmpty())
    }

    @Test
    fun emptyOverrideMeansNone() {
        val r = ClimateRepository()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.OVERRIDE, boost)
        r.onRetained("living", ClimateDoc.OVERRIDE, "")
        assertTrue(r.zones.value.getValue("living").overrides.isEmpty())
    }

    @Test
    fun emptyDecisionClearsIt() {
        val r = ClimateRepository()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        r.onRetained("living", ClimateDoc.DECISION, null)
        assertNull(r.zones.value.getValue("living").decision)
    }

    @Test
    fun malformedPayloadKeepsPrevious() {
        val logs = mutableListOf<String>()
        val r = ClimateRepository(log = { logs += it })
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        val before = r.zones.value
        r.onRetained("living", ClimateDoc.DECISION, "{")
        r.onRetained("living", ClimateDoc.OVERRIDE, """{"not":"an array"}""")
        assertEquals(before, r.zones.value)
        assertEquals(2, logs.size)
    }

    @Test
    fun clearEmptiesAll() {
        val r = ClimateRepository()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.clear()
        assertTrue(r.zones.value.isEmpty())
    }

    @Test
    fun linkUpDropsDocsTheBrokerNoLongerHolds() = runTest {
        val r = ClimateRepository(scope = backgroundScope)
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        r.onRetained("living", ClimateDoc.OVERRIDE, boost)
        r.onRetained("bed", ClimateDoc.SETTINGS, settings)
        // Offline: the boost ended (the hub deleted the retained override) and the bed zone went away.
        r.markLinkUp()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, r.zones.value.getValue("living").overrides.size, "kept during the replay window")
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(r.zones.value.getValue("living").overrides.isEmpty())
        assertEquals(setOf("living"), r.zones.value.keys)
        assertEquals(ClimateSetpoint.Temp(20.0), r.zones.value.getValue("living").decision?.setpoint)
    }

    @Test
    fun replayedDocsSurviveLinkUp() = runTest {
        val r = ClimateRepository(scope = backgroundScope)
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.OVERRIDE, boost)
        r.markLinkUp()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.OVERRIDE, boost)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(1, r.zones.value.getValue("living").overrides.size)
    }

    @Test
    fun aDecisionNotReplayedIsCleared() = runTest {
        val r = ClimateRepository(scope = backgroundScope)
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        r.onRetained("living", ClimateDoc.DECISION, decision)
        r.markLinkUp()
        r.onRetained("living", ClimateDoc.SETTINGS, settings)
        advanceTimeBy(6_000)
        runCurrent()
        assertNull(r.zones.value.getValue("living").decision)
    }
}
