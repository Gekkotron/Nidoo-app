package com.gekkotron.nidoo.dashboards

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardsRepositoryTest {

    private fun payload(id: String, name: String = id, order: Int = 0) =
        """{"id":"$id","name":"$name","order":$order,"show_header":true,"widgets":[]}"""

    private fun TestScope.repo(log: (String) -> Unit = {}) =
        DashboardsRepository(backgroundScope, log = log)

    private fun DashboardsRepository.ids() = dashboards.value.dashboards.map { it.id }

    @Test
    fun `retained message adds a dashboard`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a", "Alpha"))
        assertEquals(listOf("a"), r.ids())
        assertEquals("Alpha", r.dashboards.value.dashboards.single().name)
    }

    @Test
    fun `sorted by order then name then id`() = runTest {
        val r = repo()
        r.onRetained("z", payload("z", "a", 2))
        r.onRetained("y", payload("y", "b", 1))
        r.onRetained("x", payload("x", "B", 1))
        assertEquals(listOf("x", "y", "z"), r.ids())
    }

    @Test
    fun `empty payload deletes`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.onRetained("a", "")
        assertEquals(emptyList(), r.ids())
        r.onRetained("b", payload("b"))
        r.onRetained("b", null)
        assertEquals(emptyList(), r.ids())
    }

    @Test
    fun `malformed payload keeps the previous value`() = runTest {
        val logs = mutableListOf<String>()
        val r = repo { logs += it }
        r.onRetained("a", payload("a", "First"))
        val before = r.dashboards.value.dashboards
        r.onRetained("a", "{")
        assertEquals(before, r.dashboards.value.dashboards)
        assertEquals(1, logs.size)
    }

    @Test
    fun `not settled before the source reports its replay, even with dashboards received`() = runTest {
        val r = repo()
        r.markLinkUp()
        r.onRetained("a", payload("a"))
        runCurrent()
        advanceTimeBy(9_000)
        assertFalse(r.dashboards.value.isSettled)
    }

    @Test
    fun `settled on markReplayed`() = runTest {
        val r = repo()
        r.markLinkUp()
        r.onRetained("a", payload("a"))
        r.markReplayed()
        assertTrue(r.dashboards.value.isSettled)
        r.onRetained("c", payload("c"))
        assertTrue(r.dashboards.value.isSettled)
    }

    @Test
    fun `fallback settles 10 s after link up without markReplayed`() = runTest {
        val r = repo()
        r.markLinkUp()
        runCurrent()
        advanceTimeBy(9_999)
        assertFalse(r.dashboards.value.isSettled)
        advanceTimeBy(2)
        assertTrue(r.dashboards.value.isSettled)
    }

    @Test
    fun `a dashboard dropped by the confirm window is listed in dropped and leaves it when replayed`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.onRetained("b", payload("b"))
        r.markLinkUp()
        runCurrent()
        r.onRetained("a", payload("a"))
        advanceTimeBy(5_001)
        assertEquals(listOf("a"), r.ids())
        assertEquals(setOf("b"), r.dashboards.value.dropped)
        r.onRetained("b", payload("b"))
        assertEquals(emptySet(), r.dashboards.value.dropped)
        assertEquals(listOf("a", "b"), r.ids())
    }

    @Test
    fun `an explicit delete is not listed in dropped`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.markLinkUp()
        runCurrent()
        r.onRetained("a", "")
        advanceTimeBy(5_001)
        assertEquals(emptyList(), r.ids())
        assertEquals(emptySet(), r.dashboards.value.dropped)
    }

    @Test
    fun `a malformed replay of a known id keeps it past the confirm window`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a", "First"))
        r.markLinkUp()
        runCurrent()
        r.onRetained("a", "{")
        advanceTimeBy(5_001)
        assertEquals(listOf("a"), r.ids())
        assertEquals("First", r.dashboards.value.dashboards.single().name)
        assertEquals(emptySet(), r.dashboards.value.dropped)
    }

    @Test
    fun `an identical replay publishes no new snapshot`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        val v = r.dashboards.value.version
        r.onRetained("a", payload("a"))
        assertEquals(v, r.dashboards.value.version)
        r.onRetained("a", payload("a", "Changed"))
        assertTrue(r.dashboards.value.version > v)
    }

    @Test
    fun `an identical replay still confirms the id`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.markLinkUp()
        runCurrent()
        r.onRetained("a", payload("a"))
        advanceTimeBy(5_001)
        assertEquals(listOf("a"), r.ids())
    }

    @Test
    fun `a dashboard deleted while offline is dropped after the replay window`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.onRetained("b", payload("b"))
        r.markLinkUp()
        runCurrent()
        r.onRetained("a", payload("a"))
        advanceTimeBy(5_001)
        assertEquals(listOf("a"), r.ids())
    }

    @Test
    fun `clear empties and unsettles`() = runTest {
        val r = repo()
        r.onRetained("a", payload("a"))
        r.markReplayed()
        r.clear()
        assertEquals(emptyList(), r.ids())
        assertFalse(r.dashboards.value.isSettled)
        assertEquals(emptySet(), r.dashboards.value.dropped)
    }
}
