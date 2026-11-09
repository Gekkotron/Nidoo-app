package com.gekkotron.nidoo.climate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClimateDocsTest {

    @Test
    fun decodesSettings() {
        val s = ClimateJson.settings("""{"state":"active","entities":null,"sensor":"living_th","rules":{"heat":null}}""")
        assertEquals(ZoneSettings(state = "active", entities = null, sensor = "living_th"), s)
    }

    @Test
    fun decodesSettingsWithEntities() {
        val s = ClimateJson.settings("""{"state":"pending","entities":["a","b"]}""")
        assertEquals(ZoneSettings(state = "pending", entities = listOf("a", "b"), sensor = null), s)
    }

    @Test
    fun decodesDecisionWithBoost() {
        val d = ClimateJson.decision(
            """{"zone":"living","mode":"heat","setpoint":22.0,"source":"boost","cause":null,
               "reason":"Boost to 22.0 °C until 11:00","reading":20.4,"reading_age_s":30,"outdoor_mean":8.0,
               "home":true,"holiday":false,"applied":true,"at":1794150000,
               "entities":{"ac":{"setpoint":21.5,"mode":"heat","held":false},"trv":{"setpoint":21.5,"mode":"heat","held":false}},
               "adjust":-0.5}""",
        )
        assertEquals(
            ZoneDecision(
                mode = "heat",
                setpoint = ClimateSetpoint.Temp(22.0),
                source = "boost",
                reason = "Boost to 22.0 °C until 11:00",
                reading = 20.4,
                at = 1_794_150_000L,
                devices = setOf("ac", "trv"),
            ),
            d,
        )
    }

    @Test
    fun decodesOffSetpoint() {
        val d = ClimateJson.decision(
            """{"zone":"z","mode":null,"setpoint":"off","source":"override","reason":"","reading":null,"at":1,"entities":{}}""",
        )
        assertEquals(ClimateSetpoint.Off, d.setpoint)
        assertNull(d.mode)
        assertNull(d.reading)
    }

    @Test
    fun decodesOverrideArray() {
        val list = ClimateJson.overrides(
            """[{"entity":null,"setpoint":22.0,"mode":null,"until":1794155400,"origin":"user"},
                {"entity":"ac","setpoint":"off","mode":"heat","until":1794151000,"origin":"hand"}]""",
        )
        assertEquals(
            listOf(
                ZoneOverride(entity = null, setpoint = ClimateSetpoint.Temp(22.0), mode = null, until = 1_794_155_400L, origin = "user"),
                ZoneOverride(entity = "ac", setpoint = ClimateSetpoint.Off, mode = "heat", until = 1_794_151_000L, origin = "hand"),
            ),
            list,
        )
    }

    @Test
    fun topicsParse() {
        assertEquals("living" to ClimateDoc.DECISION, ClimateTopics.parse("nidoo/climate/zone/living/decision"))
        assertEquals("living" to ClimateDoc.SETTINGS, ClimateTopics.parse("nidoo/climate/zone/living/settings"))
        assertEquals("living" to ClimateDoc.OVERRIDE, ClimateTopics.parse("nidoo/climate/zone/living/override"))
        assertNull(ClimateTopics.parse("nidoo/climate/zone/living/hold/set"))
        assertNull(ClimateTopics.parse("nidoo/climate/house"))
        assertNull(ClimateTopics.parse("nidoo/climate/zone/living/proposal"))
        assertNull(ClimateTopics.parse("nidoo/climate/zone//decision"))
        assertNull(ClimateTopics.parse("other/climate/zone/living/decision"))
        assertEquals("nidoo/climate/zone/living/hold/set", ClimateTopics.holdCommand("living"))
    }

    @Test
    fun boostPayload() {
        assertEquals(
            Json.parseToJsonElement("""{"setpoint":22.5,"minutes":90}"""),
            Json.parseToJsonElement(ClimateCommands.boost(22.5, 90)) as JsonObject,
        )
        assertEquals("", ClimateCommands.CANCEL)
    }
}
