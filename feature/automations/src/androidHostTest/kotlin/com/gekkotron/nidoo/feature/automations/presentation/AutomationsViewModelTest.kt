package com.gekkotron.nidoo.feature.automations.presentation

import com.gekkotron.nidoo.feature.automations.resources.Res
import com.gekkotron.nidoo.feature.automations.resources.automations_toggle_failed
import com.gekkotron.nidoo.feature.automations.resources.wake_up_hub_outdated
import com.gekkotron.nidoo.feature.automations.resources.wake_up_save_failed
import com.gekkotron.nidoo.flows.FlowsClient
import com.gekkotron.nidoo.flows.FlowsHttpError
import com.gekkotron.nidoo.flows.FlowsRepository
import com.gekkotron.nidoo.flows.WakeUpTime
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class FakeFlowsClient : FlowsClient {
    val calls = mutableListOf<Pair<String, Boolean>>()
    var gate: CompletableDeferred<Result<Unit>>? = null
    var result: Result<Unit> = Result.success(Unit)

    val wakeUps = mutableListOf<List<Any>>()
    var wakeUpGate: CompletableDeferred<Result<Unit>>? = null
    var wakeUpResult: Result<Unit> = Result.success(Unit)

    override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> {
        calls += id to enabled
        return gate?.await() ?: result
    }

    override suspend fun setWakeUp(id: String, hour: Int, minute: Int, days: List<Int>): Result<Unit> {
        wakeUps += listOf(id, hour, minute, days)
        return wakeUpGate?.await() ?: wakeUpResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val client = FakeFlowsClient()
    private val areas = MutableStateFlow(listOf(Area("living", "Living room"), Area("kitchen", "Kitchen")))
    private val link = MutableStateFlow<LinkState>(LinkState.Connected(TransportMode.DEMO))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): Pair<AutomationsViewModel, FlowsRepository> {
        val repo = FlowsRepository(client, backgroundScope)
        val vm = AutomationsViewModel(repo, areas, link, dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        return vm to repo
    }

    private fun FlowsRepository.flow(id: String, name: String, area: String? = null, enabled: Boolean = true) {
        val a = area?.let { ""","area":"$it"""" } ?: ""
        onRetained(id, """{"id":"$id","name":"$name","enabled":$enabled$a}""")
    }

    @Test
    fun `loading state before settle`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.markLinkUp()
        assertTrue(vm.state.value.isLoading)
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun `empty state after settle with no flows`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.markLinkUp()
        testScheduler.advanceTimeBy(2_001)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.rows.isEmpty())
        assertEquals(listOf<AutomationChip>(AutomationChip.All), vm.state.value.chips.take(1))
    }

    @Test
    fun `rows ordered by area position when All chip selected`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "Zeta", "living")
        repo.flow("b", "alpha", "kitchen")
        repo.flow("c", "Beta", "living")
        repo.flow("d", "Orphan")
        val s = vm.state.value
        assertEquals(listOf("c", "a", "b", "d"), s.rows.map { it.id })
        assertEquals(AreaLabel.Named("Living room"), s.rows[0].area)
        assertEquals(AreaLabel.Unassigned, s.rows[3].area)
    }

    @Test
    fun `rows for a selected area hide the area label`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        repo.flow("b", "B", "kitchen")
        vm.onChipSelected("living")
        assertEquals(listOf("a"), vm.state.value.rows.map { it.id })
        assertNull(vm.state.value.rows.single().area)
    }

    @Test
    fun `flow with unknown area id falls into Unassigned bucket`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "garage")
        assertTrue(AutomationChip.Unassigned in vm.state.value.chips)
        vm.onChipSelected(UNASSIGNED_AREA)
        assertEquals(listOf("a"), vm.state.value.rows.map { it.id })
        assertNull(vm.state.value.rows.single().area)
    }

    @Test
    fun `flow with area=null falls into Unassigned bucket`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A")
        repo.flow("b", "B", "kitchen")
        repo.flow("c", "C", "living")
        assertEquals(
            listOf(AutomationChip.All, AutomationChip.Room("living", "Living room"), AutomationChip.Room("kitchen", "Kitchen"), AutomationChip.Unassigned),
            vm.state.value.chips,
        )
    }

    @Test
    fun `a room without flows gets no chip`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        assertEquals(listOf("__all__", "living"), vm.state.value.chips.map { it.key })
    }

    @Test
    fun `no Unassigned chip when every flow has a room`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        assertFalse(AutomationChip.Unassigned in vm.state.value.chips)
    }

    @Test
    fun `chip list excludes the source's own Unassigned area`() = runTest(dispatcher) {
        areas.value = areas.value + Area(UNASSIGNED_AREA, "Unassigned")
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        repo.flow("b", "B", "kitchen")
        assertEquals(listOf("__all__", "living", "kitchen"), vm.state.value.chips.map { it.key })
    }

    @Test
    fun `vanished selected chip falls back to All`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        repo.flow("b", "B", "kitchen")
        vm.onChipSelected("kitchen")
        assertEquals("kitchen", vm.state.value.selectedChipKey)
        repo.onRetained("b", null)
        assertEquals(ALL_KEY, vm.state.value.selectedChipKey)
        assertEquals(listOf("a"), vm.state.value.rows.map { it.id })
    }

    @Test
    fun `offline state disables toggles and surfaces the offline banner`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        link.value = LinkState.Reconnecting(1)
        val s = vm.state.value
        assertTrue(s.isOffline)
        assertFalse(s.rows.single().toggleEnabled)
        vm.onToggle("a", false)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun `offline never shows loading`() = runTest(dispatcher) {
        link.value = LinkState.Reconnecting(1)
        val (vm, _) = vm()
        assertTrue(vm.state.value.isOffline)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `toggle flips optimistically and clears when the repo succeeds`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        client.gate = CompletableDeferred()
        vm.onToggle("a", false)
        assertFalse(vm.state.value.rows.single().enabled)
        assertFalse(vm.state.value.rows.single().toggleEnabled)
        client.gate!!.complete(Result.success(Unit))
        assertFalse(vm.state.value.rows.single().enabled)
        assertTrue(vm.state.value.rows.single().toggleEnabled)
        assertNull(vm.state.value.toast)
    }

    @Test
    fun `toggle reverts and sets toast when the repo fails`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        client.result = Result.failure(RuntimeException("boom"))
        vm.onToggle("a", false)
        assertTrue(vm.state.value.rows.single().enabled)
        assertEquals(Res.string.automations_toggle_failed, vm.state.value.toast)
        vm.onToastShown()
        assertNull(vm.state.value.toast)
    }

    @Test
    fun `rapid toggle presses on the same row are ignored while in-flight`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        client.gate = CompletableDeferred()
        vm.onToggle("a", false)
        vm.onToggle("a", true)
        assertEquals(listOf("a" to false), client.calls)
        client.gate!!.complete(Result.success(Unit))
    }

    @Test
    fun `retained echo during in-flight toggle resolves to server truth`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "A", "living")
        client.gate = CompletableDeferred()
        vm.onToggle("a", false)
        repo.flow("a", "A", "living", enabled = true)
        client.gate!!.complete(Result.success(Unit))
        assertTrue(vm.state.value.rows.single().enabled)
    }

    private fun FlowsRepository.alarm(
        id: String,
        name: String = id,
        wakeUp: String? = """{"version":1,"hour":7,"minute":30,"days":[1,2,3,4,5]}""",
        area: String? = null,
    ) {
        val w = wakeUp?.let { ""","wake_up":$it""" } ?: ""
        val a = area?.let { ""","area":"$it"""" } ?: ""
        onRetained(id, """{"id":"$id","name":"$name","labels":["wake-up"]$a$w}""")
    }

    @Test
    fun `alarms leave the flows section`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.flow("a", "Evening", "living")
        repo.alarm("wake-up-1", "Weekdays", area = "kitchen")
        val s = vm.state.value
        assertEquals(listOf("a"), s.rows.map { it.id })
        assertEquals(listOf("wake-up-1"), s.alarms.map { it.id })
        // The alarm's room ("kitchen") gets no chip: chips come from plain flows only.
        assertEquals(listOf("__all__", "living"), s.chips.map { it.key })
    }

    @Test
    fun `alarm row carries its time`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w", "Weekdays")
        val row = vm.state.value.alarms.single()
        assertEquals(WakeUpTime(7, 30, listOf(1, 2, 3, 4, 5)), row.time)
        assertEquals("Weekdays", row.name)
        assertTrue(row.enabled)
        assertTrue(row.toggleEnabled)
    }

    @Test
    fun `alarms are sorted by time then name with unreadable ones last`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("broken", "A broken", wakeUp = "\"x\"")
        repo.alarm("late", "Late", wakeUp = """{"hour":9,"minute":0,"days":[0]}""")
        repo.alarm("b", "B early", wakeUp = """{"hour":6,"minute":0,"days":[0]}""")
        repo.alarm("a", "A early", wakeUp = """{"hour":6,"minute":0,"days":[0]}""")
        assertEquals(listOf("a", "b", "late", "broken"), vm.state.value.alarms.map { it.id })
    }

    @Test
    fun `unreadable alarm has no time and does not open`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w", wakeUp = null)
        assertNull(vm.state.value.alarms.single().time)
        vm.onAlarmTapped("w")
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `tapping an alarm opens the editor with its values`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w", "Weekdays")
        vm.onAlarmTapped("w")
        assertEquals(AlarmEditor("w", "Weekdays", 7, 30, setOf(1, 2, 3, 4, 5)), vm.state.value.editor)
    }

    @Test
    fun `offline tapping an alarm does nothing`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        link.value = LinkState.Reconnecting(1)
        vm.onAlarmTapped("w")
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `the editor keeps its copy when the flow is updated`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        vm.onAlarmTapped("w")
        vm.onEditorTime(5, 10)
        repo.alarm("w", wakeUp = """{"hour":8,"minute":0,"days":[1]}""")
        assertEquals(5, vm.state.value.editor?.hour)
    }

    @Test
    fun `the editor closes when its alarm disappears`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        vm.onAlarmTapped("w")
        repo.onRetained("w", null)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `toggling the last day off disables save`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w", wakeUp = """{"hour":7,"minute":0,"days":[3]}""")
        vm.onAlarmTapped("w")
        vm.onEditorDayToggled(3)
        assertFalse(vm.state.value.editor!!.canSave)
        vm.onEditorSave()
        assertTrue(client.wakeUps.isEmpty())
    }

    @Test
    fun `save sends sorted days and closes`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("wake-up-1", wakeUp = """{"hour":7,"minute":0,"days":[5]}""")
        vm.onAlarmTapped("wake-up-1")
        vm.onEditorTime(6, 15)
        vm.onEditorDayToggled(1)
        client.wakeUpGate = CompletableDeferred()
        vm.onEditorSave()
        assertEquals(listOf<List<Any>>(listOf("wake-up-1", 6, 15, listOf(1, 5))), client.wakeUps)
        assertTrue(vm.state.value.editor!!.saving)
        client.wakeUpGate!!.complete(Result.success(Unit))
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `save 404 keeps the editor with hub outdated`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        vm.onAlarmTapped("w")
        client.wakeUpResult = Result.failure(FlowsHttpError(404))
        vm.onEditorSave()
        val e = vm.state.value.editor!!
        assertEquals(Res.string.wake_up_hub_outdated, e.error)
        assertFalse(e.saving)
    }

    @Test
    fun `save failure keeps the editor with save failed`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        vm.onAlarmTapped("w")
        client.wakeUpResult = Result.failure(FlowsHttpError(422))
        vm.onEditorSave()
        assertEquals(Res.string.wake_up_save_failed, vm.state.value.editor!!.error)
        client.wakeUpResult = Result.failure(RuntimeException("boom"))
        vm.onEditorSave()
        assertEquals(Res.string.wake_up_save_failed, vm.state.value.editor!!.error)
    }

    @Test
    fun `section defaults to flows and switches`() = runTest(dispatcher) {
        val (vm, _) = vm()
        assertEquals(RoutineSection.Flows, vm.state.value.section)
        vm.onSectionSelected(RoutineSection.WakeUp)
        assertEquals(RoutineSection.WakeUp, vm.state.value.section)
    }

    @Test
    fun `dismissing closes the editor`() = runTest(dispatcher) {
        val (vm, repo) = vm()
        repo.alarm("w")
        vm.onAlarmTapped("w")
        vm.onEditorDismissed()
        assertNull(vm.state.value.editor)
    }
}
