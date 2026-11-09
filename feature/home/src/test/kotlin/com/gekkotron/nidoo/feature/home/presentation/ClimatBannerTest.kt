package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.climate.ClimateSetpoint
import com.gekkotron.nidoo.climate.ClimateZone
import com.gekkotron.nidoo.climate.ZoneDecision
import com.gekkotron.nidoo.climate.ZoneOverride
import com.gekkotron.nidoo.climate.ZoneSettings
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.Kind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ClimatBannerTest {
    private val now = 1_000_000L

    private val entities: Map<String, Entity> = listOf(
        testEntity("ac", Kind.CLIMATE, area = "living", capabilities = Capabilities(minTemp = 16.0, maxTemp = 30.0)),
        testEntity("trv", Kind.CLIMATE, area = "living", capabilities = Capabilities(minTemp = 5.0, maxTemp = 28.0)),
        testEntity("th", Kind.SENSOR, area = "living"),
        testEntity("bed_ac", Kind.CLIMATE, area = "bedroom"),
    ).associateBy { it.uniqueId }

    private fun decision(
        source: String = "schedule",
        setpoint: ClimateSetpoint? = ClimateSetpoint.Temp(20.0),
        devices: Set<String> = setOf("ac"),
        reading: Double? = 19.4,
    ) = ZoneDecision(mode = "heat", setpoint = setpoint, source = source, reason = "Schedule · heat", reading = reading, at = now - 60, devices = devices)

    private fun zone(
        id: String = "living",
        state: String = "active",
        entities: List<String>? = null,
        sensor: String? = null,
        decision: ZoneDecision? = decision(),
        overrides: List<ZoneOverride> = emptyList(),
    ) = ClimateZone(id, ZoneSettings(state, entities, sensor), decision, overrides)

    private fun boost(target: Double = 22.0, until: Long = now + 3600) =
        ZoneOverride(entity = null, setpoint = ClimateSetpoint.Temp(target), mode = null, until = until, origin = "user")

    private fun banner(vararg zones: ClimateZone, area: String = "living") = climatBannerFor(area, zones.toList(), entities, now)

    @Test
    fun noZonesNoBanner() {
        assertNull(banner())
    }

    @Test
    fun pendingAndOffZonesNoBanner() {
        assertNull(banner(zone(state = "pending")))
        assertNull(banner(zone(state = "off")))
        assertNull(banner(ClimateZone("living", settings = null, decision = decision(), overrides = emptyList())))
    }

    @Test
    fun matchesByDecisionDevicesWhenEntitiesNull() {
        val b = assertNotNull(banner(zone(entities = null, decision = decision(devices = setOf("ac")))))
        assertEquals("living", b.zone)
        assertEquals(19.4, b.reading)
        assertEquals(20.0, b.target)
        assertEquals(ClimatWhy.SCHEDULE, b.why)
        assertEquals("Schedule · heat", b.reason)
        assertNull(b.boost)
        assertTrue(b.canBoost)
    }

    @Test
    fun matchesBySettingsEntitiesWithoutDecision() {
        val b = assertNotNull(banner(zone(entities = listOf("ac"), decision = null)))
        assertNull(b.reading)
        assertNull(b.target)
        assertEquals(ClimatWhy.OTHER, b.why)
    }

    @Test
    fun matchesBySensorOnly() {
        assertNotNull(banner(zone(sensor = "th", decision = decision(devices = emptySet()))))
    }

    @Test
    fun otherRoomNoBanner() {
        assertNull(banner(zone(), area = "bedroom"))
        assertNull(banner(zone(decision = decision(devices = setOf("unknown")))))
    }

    @Test
    fun liveBoostShown() {
        val b = assertNotNull(banner(zone(decision = decision(source = "boost", setpoint = ClimateSetpoint.Temp(22.0)), overrides = listOf(boost()))))
        assertEquals(ClimatWhy.BOOST, b.why)
        assertEquals(ClimatBoostUi(target = 22.0, until = now + 3600), b.boost)
        assertNull(b.holdUntil)
    }

    @Test
    fun expiredBoostIgnored() {
        val b = assertNotNull(banner(zone(overrides = listOf(boost(until = now)))))
        assertNull(b.boost)
        assertNull(b.holdUntil)
    }

    @Test
    fun oldHubOverrideWithTempIsBoost() {
        val b = assertNotNull(banner(zone(decision = decision(source = "override", setpoint = ClimateSetpoint.Temp(22.0)), overrides = listOf(boost()))))
        assertEquals(ClimatWhy.BOOST, b.why)
        assertEquals(22.0, b.boost?.target)
    }

    @Test
    fun modeOnlyOverrideIsHold() {
        val hold = ZoneOverride(entity = null, setpoint = null, mode = "cool", until = now + 600, origin = "user")
        val off = hold.copy(setpoint = ClimateSetpoint.Off, mode = null)
        for (o in listOf(hold, off)) {
            val b = assertNotNull(banner(zone(decision = decision(source = "override"), overrides = listOf(o))))
            assertEquals(ClimatWhy.HOLD, b.why)
            assertNull(b.boost)
            assertEquals(now + 600, b.holdUntil)
        }
    }

    @Test
    fun deviceHoldIsNotAZoneHold() {
        val hand = ZoneOverride(entity = "ac", setpoint = ClimateSetpoint.Temp(23.0), mode = null, until = now + 600, origin = "hand")
        val b = assertNotNull(banner(zone(overrides = listOf(hand))))
        assertNull(b.boost)
        assertNull(b.holdUntil)
    }

    @Test
    fun safetyHidesBoost() {
        val b = assertNotNull(banner(zone(decision = decision(source = "safety"))))
        assertEquals(ClimatWhy.SAFETY, b.why)
        assertFalse(b.canBoost)
    }

    @Test
    fun whyFollowsTheSource() {
        val cases = mapOf(
            "eco" to ClimatWhy.ECO, "away" to ClimatWhy.AWAY, "holiday" to ClimatWhy.HOLIDAY,
            "empty" to ClimatWhy.EMPTY, "idle" to ClimatWhy.IDLE, "something_new" to ClimatWhy.OTHER,
        )
        for ((source, why) in cases) assertEquals(why, banner(zone(decision = decision(source = source)))?.why, source)
    }

    @Test
    fun offSetpointHasNoTarget() {
        assertNull(banner(zone(decision = decision(setpoint = ClimateSetpoint.Off)))?.target)
    }

    @Test
    fun rangeIsIntersectionOfDevices() {
        val b = assertNotNull(banner(zone(decision = decision(devices = setOf("ac", "trv")))))
        assertEquals(16.0, b.min)
        assertEquals(28.0, b.max)
        val bare = assertNotNull(banner(zone(sensor = "th", decision = decision(devices = emptySet()))))
        assertEquals(7.0, bare.min)
        assertEquals(35.0, bare.max)
    }

    @Test
    fun twoZonesPickMostMatchesThenId() {
        val one = zone(id = "b_one", decision = decision(devices = setOf("ac")))
        val two = zone(id = "z_two", decision = decision(devices = setOf("ac", "trv")))
        assertEquals("z_two", banner(one, two)?.zone)
        val tie = zone(id = "a_tie", decision = decision(devices = setOf("trv")))
        assertEquals("a_tie", banner(one, tie)?.zone)
    }
}
