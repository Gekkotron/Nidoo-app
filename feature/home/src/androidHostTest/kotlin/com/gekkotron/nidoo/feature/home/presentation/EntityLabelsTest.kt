package com.gekkotron.nidoo.feature.home.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EntityLabelsTest {
    @Test
    fun `normalize trims, lowercases, folds separators and collapses whitespace`() {
        assertEquals("local temperature", EntityLabels.normalize("  Local_Temperature "))
        assertEquals("battery low", EntityLabels.normalize("Battery-low"))
        assertEquals("co2", EntityLabels.normalize("CO2"))
        assertEquals("water leak", EntityLabels.normalize("water__leak"))
    }

    @Test
    fun `resource resolves known labels regardless of case or separators`() {
        assertNotNull(EntityLabels.resource("Temperature"))
        assertNotNull(EntityLabels.resource("local_temperature"))
        assertNotNull(EntityLabels.resource("BATTERY"))
    }

    @Test
    fun `resource returns null for unknown labels`() {
        assertNull(EntityLabels.resource("Frobnicator"))
        assertNull(EntityLabels.resource(""))
    }

    @Test
    fun `every mapped label resolves to a non-blank English string`() {
        EntityLabels.keys.forEach { key ->
            val resource = assertNotNull(EntityLabels.resource(key), "no resource for '$key'")
            assertTrue(runBlocking { getString(resource) }.isNotBlank(), "blank EN string for '$key'")
        }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `every mapped label resolves to a non-blank French string`() {
        EntityLabels.keys.forEach { key ->
            val resource = assertNotNull(EntityLabels.resource(key), "no resource for '$key'")
            assertTrue(runBlocking { getString(resource) }.isNotBlank(), "blank FR string for '$key'")
        }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `temperature translates to Temperature in French`() {
        assertEquals("Température", runBlocking { getString(EntityLabels.resource("temperature")!!) })
    }
}
