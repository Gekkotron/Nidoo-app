package com.gekkotron.oikos.feature.home.presentation

import com.gekkotron.oikos.dashboards.DashboardsRepository
import com.gekkotron.oikos.data.AppPreferences
import com.gekkotron.oikos.data.DashboardChoice
import com.gekkotron.oikos.feature.home.FakeEntitySource
import com.gekkotron.oikos.feature.home.testEntity
import com.gekkotron.oikos.flows.FlowsClient
import com.gekkotron.oikos.flows.FlowsHttpError
import com.gekkotron.oikos.flows.FlowsRepository
import com.gekkotron.oikos.history.DailyTotals
import com.gekkotron.oikos.history.EnergyScale
import com.gekkotron.oikos.history.HistoryClient
import com.gekkotron.oikos.history.HistoryRange
import com.gekkotron.oikos.history.HistoryRef
import com.gekkotron.oikos.history.HistoryResult
import com.gekkotron.oikos.history.Series
import com.gekkotron.oikos.model.EnergyRole
import com.gekkotron.oikos.model.Kind
import com.gekkotron.oikos.model.UiOverride
import com.gekkotron.oikos.transport.LinkState
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class RunClient : FlowsClient {
    val runs = mutableListOf<String>()
    var gate: CompletableDeferred<Result<Unit>>? = null
    var result: Result<Unit> = Result.success(Unit)
    override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> = result
    override suspend fun run(id: String): Result<Unit> {
        runs += id
        return gate?.await() ?: result
    }
}

private class CountingHistory : HistoryClient {
    var calls = 0
    val firstCall = CompletableDeferred<Unit>()
    override val available: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun series(ref: HistoryRef, range: HistoryRange): HistoryResult<Series> {
        calls++
        firstCall.complete(Unit)
        return HistoryResult.Unavailable("test")
    }
    override suspend fun daily(ref: HistoryRef, days: Int, scale: EnergyScale): HistoryResult<DailyTotals> {
        calls++
        firstCall.complete(Unit)
        return HistoryResult.Unavailable("test")
    }
    override suspend fun today(ref: HistoryRef, scale: EnergyScale): HistoryResult<Double?> {
        calls++
        firstCall.complete(Unit)
        return HistoryResult.Unavailable("test")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val source = FakeEntitySource()
    private val client = RunClient()
    private val history = CountingHistory()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    private val prefsScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val rigs = mutableListOf<Rig>()

    @After
    fun tearDown() {
        // Stop the ViewModel and DataStore before Main goes away, or a late DataStore write dispatches into a dead Main.
        // cancel() only requests it: a coroutine still cancelling can be resumed from DataStore's thread after
        // resetMain, so wait for every job to finish while Main is still set.
        runBlocking {
            withTimeout(10_000) {
                rigs.forEach { it.vm.viewModelScope.coroutineContext.job.cancelAndJoin() }
                prefsScope.coroutineContext.job.cancelAndJoin()
            }
        }
        Dispatchers.resetMain()
    }

    private class Rig(val vm: DashboardViewModel, val repo: DashboardsRepository, val flows: FlowsRepository, val prefs: AppPreferences)

    private suspend fun TestScope.rig(saved: DashboardChoice? = null, refresh: Duration = 5.minutes): Rig {
        val repo = DashboardsRepository(backgroundScope)
        val flows = FlowsRepository(client, backgroundScope)
        val prefs = seededPrefs(saved)
        val vm = DashboardViewModel(source, repo, flows, prefs, history, LabelText { null }, dispatcher, refresh, clock = { 1_800_000_000_000L })
        backgroundScope.launch { vm.state.collect {} }
        return Rig(vm, repo, flows, prefs).also { rigs += it }
    }

    /** A fresh preferences file, holding [saved] when given. The setter returns once the choice is stored and shown. */
    private suspend fun seededPrefs(saved: DashboardChoice?): AppPreferences {
        val prefs = AppPreferences.create(File.createTempFile("prefs", ".json").also { it.delete() }, prefsScope)
        if (saved != null) prefs.setSelectedDashboard(saved.id, saved.name)
        return prefs
    }

    private fun DashboardsRepository.put(id: String, name: String, widgets: String = "[]") =
        onRetained(id, """{"name":"$name","widgets":$widgets}""")

    private suspend fun Rig.choice(want: DashboardChoice) {
        // Real time: DataStore answers from its own thread, and a virtual timeout would fire before it does.
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { prefs.selectedDashboard.first { it == want } } }
    }

    @Test
    fun `selecting a dashboard saves its id and name`() = runTest {
        val r = rig()
        r.repo.put("a", "Upstairs")
        r.vm.select("a")
        r.choice(DashboardChoice("a", "Upstairs"))
        val shown = r.vm.state.first { it.view is DashboardView.Shown }
        assertEquals("a", shown.selectedId)
        assertTrue(shown.switcherVisible)
        assertEquals(listOf("house", "a"), shown.entries.map { it.id })
        r.vm.select("house")
        r.choice(DashboardChoice("house", null))
    }

    @Test
    fun `a dashboard deleted on the hub resets the choice to House`() = runTest {
        val r = rig()
        r.repo.put("a", "Upstairs")
        r.vm.select("a")
        r.choice(DashboardChoice("a", "Upstairs"))
        r.repo.markReplayed()
        r.repo.onRetained("a", null)
        r.choice(DashboardChoice("house", null))
        assertEquals(DashboardView.House, r.vm.state.first { it.view == DashboardView.House }.view)
    }

    @Test
    fun `a rename rewrites the stored name`() = runTest {
        val r = rig()
        r.repo.put("a", "Upstairs")
        r.vm.select("a")
        r.choice(DashboardChoice("a", "Upstairs"))
        r.repo.put("a", "Attic")
        r.choice(DashboardChoice("a", "Attic"))
    }

    @Test
    fun `running a flow shows it running then clears`() = runTest {
        val r = rig()
        r.flows.onRetained("f", """{"id":"f","name":"Night","enabled":true}""")
        r.repo.put("a", "A", """[{"id":"w","kind":"flow_button","span":"1x1","config":{"flow_id":"f"}}]""")
        r.vm.select("a")
        r.vm.state.first { it.view is DashboardView.Shown }
        client.gate = CompletableDeferred()
        r.vm.runFlow("f")
        r.vm.state.first { "f" in it.runningFlows }
        r.vm.runFlow("f") // no-op while running
        client.gate!!.complete(Result.success(Unit))
        r.vm.state.first { it.runningFlows.isEmpty() }
        assertEquals(listOf("f"), client.runs)
    }

    @Test
    fun `a 422 is reported as refused`() = runTest {
        val r = rig()
        client.result = Result.failure(FlowsHttpError(422))
        r.vm.runFlow("f")
        assertEquals(DashboardEvent.FlowRunFailed(refused = true), r.vm.events.first())
        client.result = Result.failure(FlowsHttpError(500))
        r.vm.runFlow("f")
        assertEquals(DashboardEvent.FlowRunFailed(refused = false), r.vm.events.first())
    }

    @Test
    fun `offline run fails without a request`() = runTest {
        val r = rig()
        source.link.value = LinkState.Connecting
        r.vm.runFlow("f")
        assertEquals(DashboardEvent.FlowRunFailed(refused = false), r.vm.events.first())
        assertEquals(emptyList(), client.runs)
    }

    @Test
    fun `energy figures are fetched only when an energy tile is shown`() = runTest {
        source.entities.value = mapOf("pv" to testEntity("pv", Kind.SENSOR, unit = "W", ui = UiOverride(energyRole = EnergyRole.SOLAR)))
        val r = rig()
        r.repo.put("plain", "Plain", """[{"id":"w","kind":"light","span":"1x1","config":{"entity_id":"x"}}]""")
        r.repo.put("energy", "Energy", """[{"id":"w","kind":"energy_stat","span":"1x1","config":{"metric":"today_kwh"}}]""")
        r.vm.select("plain")
        r.vm.state.first { it.selectedId == "plain" && it.view is DashboardView.Shown }
        assertEquals(0, history.calls)
        r.vm.select("energy")
        r.vm.state.first { it.selectedId == "energy" && it.view is DashboardView.Shown }
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { history.firstCall.await() } }
        assertTrue(history.calls > 0, "an energy tile asks the history service")
        val tile = ((r.vm.state.value.view as DashboardView.Shown).ui.tiles.single().content)
        assertIs<TileContent.EnergyStat>(tile)
    }

    private suspend fun <T> real(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }

    @Test
    fun `a saved dashboard survives another one replaying first`() = runTest {
        val r = rig(saved = DashboardChoice("b", "B"))
        r.repo.put("a", "A")
        r.repo.put("b", "B")
        val shown = real { r.vm.state.first { it.view is DashboardView.Shown } }
        assertEquals("b", shown.selectedId)
        assertEquals(DashboardChoice("b", "B"), r.prefs.selectedDashboard.value)
    }

    @Test
    fun `the first state follows the saved choice instead of flashing House`() = runTest {
        val r = rig(saved = DashboardChoice("b", "B"))
        assertEquals(DashboardView.Loading("B"), r.vm.state.value.view)
        assertTrue(r.vm.state.value.switcherVisible)
    }

    @Test
    fun `an unrelated snapshot change does not re-request history`() = runTest {
        source.entities.value = mapOf("pv" to testEntity("pv", Kind.SENSOR, unit = "W", ui = UiOverride(energyRole = EnergyRole.SOLAR)))
        // Virtual time runs on while the test waits in real time, so a finite refresh would add fetches of its own.
        val r = rig(refresh = Duration.INFINITE)
        r.repo.put("energy", "Energy", """[{"id":"w","kind":"energy_stat","span":"1x1","config":{"metric":"today_kwh"}}]""")
        r.vm.select("energy")
        real { history.firstCall.await() }
        r.vm.state.first { it.view is DashboardView.Shown }
        val before = history.calls
        r.repo.put("other", "Other")
        real { r.vm.state.first { it.entries.size == 3 } }
        kotlinx.coroutines.yield()
        assertEquals(before, history.calls)
    }

    @Test
    fun `a saved dashboard replayed 3 s after link-up is still shown and kept`() = runTest {
        val r = rig(saved = DashboardChoice("b", "B"))
        r.repo.markLinkUp()
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()
        assertEquals(DashboardView.Loading("B"), r.vm.state.value.view)
        r.repo.put("b", "B")
        r.repo.markReplayed()
        val shown = real { r.vm.state.first { it.view is DashboardView.Shown } }
        assertEquals("b", shown.selectedId)
        assertEquals(DashboardChoice("b", "B"), r.prefs.selectedDashboard.value)
    }

    @Test
    fun `a selected dashboard dropped on reconnect shows House but keeps the preference, and comes back when replayed`() = runTest {
        val r = rig(saved = DashboardChoice("b", "B"))
        r.repo.put("a", "A")
        r.repo.put("b", "B")
        r.repo.markReplayed()
        real { r.vm.state.first { it.view is DashboardView.Shown } }
        r.repo.markLinkUp()
        r.repo.onRetained("a", """{"name":"A","widgets":[]}""")
        testScheduler.advanceTimeBy(5_001)
        testScheduler.runCurrent()
        val house = real { r.vm.state.first { it.view == DashboardView.House } }
        assertEquals("house", house.selectedId)
        assertEquals(DashboardChoice("b", "B"), r.prefs.selectedDashboard.value)
        r.repo.put("b", "B")
        val back = real { r.vm.state.first { it.view is DashboardView.Shown } }
        assertEquals("b", back.selectedId)
        assertEquals(DashboardChoice("b", "B"), r.prefs.selectedDashboard.value)
    }
}
