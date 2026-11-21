package com.gekkotron.nidoo.feature.home.presentation

import androidx.lifecycle.SavedStateHandle
import com.gekkotron.nidoo.feature.home.FakeEntitySource
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandFailure
import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UiOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whole-room buttons (rooms design §6.3): what is sent, what blocks it, and how failures come back. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelRoomTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val preset = UiOverride(lightOnPreset = "warm")
    private val source = FakeEntitySource().apply {
        entities.value = listOf(
            testEntity("a", Kind.LIGHT, "Lamp A", area = "living", ui = preset, capabilities = Capabilities(brightness = true)),
            testEntity("b", Kind.LIGHT, "Lamp B", area = "living"),
            testEntity("c", Kind.COVER, "Shutter", area = "living"),
        ).associateBy { it.uniqueId }
        areas.value = listOf(Area("living", "Living room"))
        stateMap.value = mapOf(
            "a" to EntityState("a", StateValue.Light(true)),
            "b" to EntityState("b", StateValue.Light(false)),
            "c" to EntityState("c", StateValue.Cover(CoverState.OPEN, 100)),
        )
    }
    private val events = mutableListOf<HomeEvent>()
    private val warm = Command.TurnOn(brightness = 200)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.events.collect { events += it } }
        return vm
    }

    private fun lights(a: Boolean, b: Boolean) {
        source.stateMap.value += mapOf("a" to EntityState("a", StateValue.Light(a)), "b" to EntityState("b", StateValue.Light(b)))
    }

    @Test
    fun `the lights button turns off what is on`() = runTest(dispatcher) {
        viewModel().sendRoom("living", RoomKind.LIGHT)
        assertEquals(listOf<Pair<String, Command>>("a" to Command.TurnOff), source.sent)
    }

    @Test
    fun `the lights button turns every light on with its own preset`() = runTest(dispatcher) {
        lights(a = false, b = false)
        viewModel().sendRoom("living", RoomKind.LIGHT)
        assertEquals(setOf<Pair<String, Command>>("a" to warm, "b" to Command.TurnOn()), source.sent.toSet())
        assertEquals(2, source.sent.size)
    }

    @Test
    fun `the shutters button closes, then opens`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendRoom("living", RoomKind.COVER)
        source.stateMap.value += "c" to EntityState("c", StateValue.Cover(CoverState.CLOSED, 0))
        vm.sendRoom("living", RoomKind.COVER)
        assertEquals(listOf<Pair<String, Command>>("c" to Command.Close, "c" to Command.Open), source.sent)
    }

    @Test
    fun `a stale light blocks the room and asks to wait for the connection`() = runTest(dispatcher) {
        source.stateMap.value += "a" to EntityState("a", StateValue.Light(true), stale = true)
        viewModel().sendRoom("living", RoomKind.LIGHT)
        assertTrue(source.sent.isEmpty())
        assertEquals(listOf<HomeEvent>(HomeEvent.WaitingForConnection), events)
    }

    @Test
    fun `a room whose lights are all unavailable sends nothing and says nothing`() = runTest(dispatcher) {
        source.stateMap.value += mapOf(
            "a" to EntityState("a", StateValue.Light(true), available = false),
            "b" to EntityState("b", StateValue.Light(false), available = false),
        )
        viewModel().sendRoom("living", RoomKind.LIGHT)
        assertTrue(source.sent.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an unknown room sends nothing`() = runTest(dispatcher) {
        viewModel().sendRoom("attic", RoomKind.LIGHT)
        assertTrue(source.sent.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `failures of one room tap share one snackbar, even with different commands`() = runTest(dispatcher) {
        lights(a = false, b = false)
        viewModel().sendRoom("living", RoomKind.LIGHT)
        source.commandFailures.emit(CommandFailure("a", warm, "timeout"))
        source.commandFailures.emit(CommandFailure("b", Command.TurnOn(), "timeout"))
        advanceTimeBy(1_100)
        assertEquals(
            listOf<HomeEvent>(
                HomeEvent.RoomNotConfirmed(
                    "room:living:light", "living", RoomKind.LIGHT,
                    mapOf("a" to warm, "b" to Command.TurnOn()), listOf("Lamp A", "Lamp B"),
                ),
            ),
            events,
        )
    }

    @Test
    fun `Retry repeats the failed devices only, each with its command`() = runTest(dispatcher) {
        lights(a = false, b = false)
        val vm = viewModel()
        vm.sendRoom("living", RoomKind.LIGHT)
        source.commandFailures.emit(CommandFailure("a", warm, "timeout"))
        advanceTimeBy(1_100)
        source.sent.clear()
        vm.retryRoom(events.single() as HomeEvent.RoomNotConfirmed)
        assertEquals(listOf<Pair<String, Command>>("a" to warm), source.sent)
        // The retry is tracked like the first send: failing again comes back as one room snackbar.
        events.clear()
        source.commandFailures.emit(CommandFailure("a", warm, "timeout"))
        advanceTimeBy(1_100)
        assertEquals(listOf("a"), (events.single() as HomeEvent.RoomNotConfirmed).failed.keys.toList())
    }

    @Test
    fun `lights and shutters of the same room fail separately`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendRoom("living", RoomKind.LIGHT)
        vm.sendRoom("living", RoomKind.COVER)
        source.commandFailures.emit(CommandFailure("a", Command.TurnOff, "timeout"))
        source.commandFailures.emit(CommandFailure("c", Command.Close, "timeout"))
        advanceTimeBy(1_100)
        assertEquals(listOf("room:living:light", "room:living:cover"), events.map { (it as HomeEvent.RoomNotConfirmed).sendId })
    }

    @Test
    fun `a failure for a command the room did not send stays a single NotConfirmed`() = runTest(dispatcher) {
        viewModel().sendRoom("living", RoomKind.LIGHT)
        source.commandFailures.emit(CommandFailure("a", Command.TurnOn(), "timeout"))
        advanceTimeBy(1_100)
        assertEquals(listOf<HomeEvent>(HomeEvent.NotConfirmed("a", Command.TurnOn())), events)
    }

    @Test
    fun `a rejected room command reports the room send id once`() = runTest(dispatcher) {
        lights(a = false, b = false)
        source.result = CommandResult.Rejected("read-only")
        viewModel().sendRoom("living", RoomKind.LIGHT)
        assertEquals(listOf<HomeEvent>(HomeEvent.Rejected("room:living:light", "read-only")), events)
    }

    @Test
    fun `selecting a view clears the search`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search("lamp")
        assertEquals("lamp", vm.state.value.query)
        vm.select(RoomFilter.Rooms)
        assertEquals("", vm.state.value.query)
        assertEquals(RoomFilter.Rooms, vm.state.value.selected)
    }
}
