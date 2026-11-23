package com.gekkotron.nidoo.feature.home.di

import com.gekkotron.nidoo.format.formatLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PreloadedLabelTextTest {
    private val en = formatLocale("en-US")
    private val fr = formatLocale("fr-FR")
    private val english = mapOf("temperature" to "Temperature", "local temperature" to "Local temperature")
    private val french = mapOf("temperature" to "Température", "local temperature" to "Température locale")

    // Not backgroundScope: advanceUntilIdle does not wait for the tasks in it.
    private fun TestScope.loadScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    @Test
    fun unknownUntilLoadedThenTranslated() = runTest {
        val labels = PreloadedLabelText(loadScope(), { fr }, { delay(10); french })
        assertNull(labels("Temperature"))
        advanceUntilIdle()
        assertEquals("Température locale", labels("Local_Temperature"))
    }

    @Test
    fun reloadsWhenTheLocaleChanges() = runTest {
        var locale = en
        val labels = PreloadedLabelText(loadScope(), { locale }, { delay(10); if (locale == fr) french else english })
        advanceUntilIdle()
        assertEquals("Temperature", labels("Temperature"))
        locale = fr
        // The old map keeps answering while the French one loads.
        assertEquals("Temperature", labels("Temperature"))
        advanceUntilIdle()
        assertEquals("Température", labels("Temperature"))
    }

    @Test
    fun updatesFireWhenLabelsLoad() = runTest {
        val labels = PreloadedLabelText(loadScope(), { en }, { delay(10); english })
        val seen = mutableListOf<Unit>()
        val collector = loadScope().launch { labels.updates.collect { seen += it } }
        runCurrent()
        assertEquals(1, seen.size)
        advanceUntilIdle()
        assertEquals(2, seen.size)
        collector.cancel()
    }

    @Test
    fun oneReloadAtATime() = runTest {
        var loads = 0
        val labels = PreloadedLabelText(loadScope(), { en }, { loads++; delay(10); english })
        labels("Temperature")
        labels("Humidity")
        advanceUntilIdle()
        assertEquals(1, loads)
    }
}
