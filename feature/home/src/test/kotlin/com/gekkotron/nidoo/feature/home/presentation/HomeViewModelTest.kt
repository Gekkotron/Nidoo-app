package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.climate.ClimateDoc
import com.gekkotron.nidoo.climate.ClimateRepository
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import androidx.lifecycle.SavedStateHandle
import com.gekkotron.nidoo.feature.home.FakeEntitySource
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandFailure
import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val source = FakeEntitySource().apply {
        entities.value = mapOf(
            "lamp" to testEntity("lamp", Kind.LIGHT, area = "kitchen"),
            "shutter" to testEntity("shutter", Kind.COVER, area = "kitchen"),
        )
        areas.value = listOf(Area("kitchen", "Kitchen"))
        stateMap.value = mapOf("lamp" to EntityState("lamp", StateValue.Light(true, 255)))
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state reflects the source`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        assertFalse(vm.state.value.loading)
        assertEquals(setOf("lamp", "shutter"), vm.state.value.cards.map { it.id }.toSet())
    }

    @Test
    fun `selected room survives recreation`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        HomeViewModel(source, handle, dispatcher, preferences = testPreferences()).select(RoomFilter.Area("kitchen"))
        val recreated = HomeViewModel(source, handle, dispatcher, preferences = testPreferences())
        backgroundScope.launch { recreated.state.collect {} }
        assertEquals(RoomFilter.Area("kitchen"), recreated.state.value.selected)
    }

    @Test
    fun `a stale Hidden selection resets to All instead of reappearing once something else is hidden`() = runTest(dispatcher) {
        source.entities.value += "secret" to testEntity("secret", Kind.SWITCH, area = "kitchen", ui = UiOverride(hidden = true))
        val handle = SavedStateHandle()
        val vm = HomeViewModel(source, handle, dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Hidden)
        assertEquals(RoomFilter.Hidden, vm.state.value.selected)
        // Unhiding the last hidden card drops the Hidden chip; the view falls back to All.
        source.entities.value += "secret" to testEntity("secret", Kind.SWITCH, area = "kitchen")
        assertEquals(RoomFilter.All, vm.state.value.selected)
        // Hiding another card brings the Hidden chip back; a stale saved key would jump back to it.
        source.entities.value += "lamp" to testEntity("lamp", Kind.LIGHT, area = "kitchen", ui = UiOverride(hidden = true))
        assertEquals(RoomFilter.All, vm.state.value.selected)
    }

    @Test
    fun `launch lands on Favorites even when the favourite flags arrive after the entities`() = runTest(dispatcher) {
        // Cold start: discovery configs come in first, the retained nidoo/ui overrides a moment later.
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(RoomFilter.All, vm.state.value.selected)
        source.entities.value += "lamp" to testEntity("lamp", Kind.LIGHT, area = "kitchen", ui = UiOverride(favorite = true))
        assertEquals(RoomFilter.Favorites, vm.state.value.selected)
        assertEquals(RoomFilter.Favorites, vm.state.value.chips.first().filter)
    }

    @Test
    fun `picking All by hand sticks when favourites show up later`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.All)
        source.entities.value += "lamp" to testEntity("lamp", Kind.LIGHT, area = "kitchen", ui = UiOverride(favorite = true))
        assertEquals(RoomFilter.All, vm.state.value.selected)
    }

    @Test
    fun `tapping sends an absolute on-off so Retry repeats the intent, and ignores sheet cards`() = runTest(dispatcher) {
        source.entities.value += "off" to testEntity("off", Kind.SWITCH, area = "kitchen")
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val cards = vm.state.value.cards.associateBy { it.id }
        vm.tap(cards.getValue("lamp"))
        vm.tap(cards.getValue("off"))
        vm.tap(cards.getValue("shutter"))
        assertEquals(listOf("lamp" to Command.TurnOff, "off" to Command.TurnOn()), source.sent)
    }

    @Test
    fun `tapping a light with a saved on-preset sends that colour and brightness, not a bare on`() = runTest(dispatcher) {
        source.entities.value = mapOf(
            "led" to testEntity(
                "led", Kind.LIGHT, area = "kitchen",
                capabilities = Capabilities(brightness = true, colorModes = setOf("hs")),
                ui = UiOverride(lightOnPreset = "white"),
            ),
        )
        source.stateMap.value = mapOf("led" to EntityState("led", StateValue.Light(false, 10)))
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val led = vm.state.value.card("led")!!
        vm.tap(led)
        assertEquals("led" to Command.TurnOn(brightness = 255, color = com.gekkotron.nidoo.model.Hs(0.0, 0.0)), source.sent.last())
    }

    @Test
    fun `a saved on-preset brightness overrides the preset's baked value`() = runTest(dispatcher) {
        source.entities.value = mapOf(
            "led" to testEntity(
                "led", Kind.LIGHT, area = "kitchen",
                capabilities = Capabilities(brightness = true, colorModes = setOf("hs")),
                ui = UiOverride(lightOnPreset = "white", lightOnBrightness = 80),
            ),
        )
        source.stateMap.value = mapOf("led" to EntityState("led", StateValue.Light(false, 10)))
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.tap(vm.state.value.card("led")!!)
        assertEquals("led" to Command.TurnOn(brightness = 80, color = com.gekkotron.nidoo.model.Hs(0.0, 0.0)), source.sent.last())
    }

    @Test
    fun `unconfirmed command becomes a NotConfirmed event`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        source.commandFailures.emit(CommandFailure("lamp", Command.Toggle, "timeout"))
        assertEquals(HomeEvent.NotConfirmed("lamp", Command.Toggle), vm.events.first())
    }

    @Test
    fun `rejected command becomes a Rejected event`() = runTest(dispatcher) {
        source.result = CommandResult.Rejected("unavailable")
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.send("lamp", Command.TurnOff)
        assertEquals(HomeEvent.Rejected("lamp", "unavailable"), vm.events.first())
    }

    @Test
    fun `a burst of state updates collapses, with the final state reflecting the last update`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        repeat(50) { i ->
            source.stateMap.value = mapOf("lamp" to EntityState("lamp", StateValue.Light(on = i % 2 == 0, brightness = i)))
        }
        val lamp = vm.state.value.cards.first { it.id == "lamp" }
        assertFalse(lamp.on)
    }

    @Test
    fun `customize writes the override`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.customize("lamp", emptyList(), UiOverride(name = "Ceiling"))
        assertEquals(UiOverride(name = "Ceiling"), source.overrides["lamp"])
        assertEquals(listOf("override"), source.calls)
    }

    @Test
    fun `customize failure emits SaveFailed`() = runTest(dispatcher) {
        source.failWrites = true
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.customize("lamp", emptyList(), UiOverride(hidden = true))
        assertEquals(HomeEvent.SaveFailed, vm.events.first())
    }

    @Test
    fun `customize with a new room publishes the areas, then the override, in order`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.customize("lamp", listOf(Area("office", "Office")), UiOverride(area = "office"))
        assertEquals(listOf("areas", "override"), source.calls)
        assertEquals(listOf(listOf(Area("kitchen", "Kitchen"), Area("office", "Office"))), source.areaWrites)
        assertEquals(UiOverride(area = "office"), source.overrides["lamp"])
    }

    @Test
    fun `when publishing the new room fails, the override is never sent`() = runTest(dispatcher) {
        source.failWrites = true
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.customize("lamp", listOf(Area("office", "Office")), UiOverride(area = "office"))
        assertEquals(listOf("areas"), source.calls)
        assertEquals(emptyMap(), source.overrides)
        assertEquals(HomeEvent.SaveFailed, vm.events.first())
    }

    @Test
    fun `a room typed and then left for another is not published`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        vm.customize("lamp", listOf(Area("office", "Office"), Area("attic", "Attic")), UiOverride(area = "attic"))
        assertEquals(listOf(listOf(Area("kitchen", "Kitchen"), Area("attic", "Attic"))), source.areaWrites)
    }

    @Test
    fun `a stale card is dimmed and neither a tap nor a sheet command sends`() = runTest(dispatcher) {
        source.stateMap.value = mapOf(
            "lamp" to EntityState("lamp", StateValue.Light(true, 255), stale = true),
            "shutter" to EntityState("shutter", StateValue.Unknown, stale = true),
        )
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        val lamp = vm.state.value.cards.first { it.id == "lamp" }
        assertTrue(lamp.stale)
        vm.tap(lamp)
        vm.send("shutter", Command.Open)
        assertEquals(emptyList(), source.sent)
        assertEquals(listOf<HomeEvent>(HomeEvent.WaitingForConnection, HomeEvent.WaitingForConnection), events)
    }

    @Test
    fun `a live state is not stale and sends`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val lamp = vm.state.value.cards.first { it.id == "lamp" }
        assertFalse(lamp.stale)
        vm.tap(lamp)
        assertEquals(listOf<Pair<String, Command>>("lamp" to Command.TurnOff), source.sent)
    }

    @Test
    fun `search query filters and survives recreation`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        HomeViewModel(source, handle, dispatcher, preferences = testPreferences()).search("shut")
        val recreated = HomeViewModel(source, handle, dispatcher, preferences = testPreferences())
        backgroundScope.launch { recreated.state.collect {} }
        assertEquals("shut", recreated.state.value.query)
        assertEquals(listOf("shutter"), recreated.state.value.cards.map { it.id })
    }

    private fun groupSource() = FakeEntitySource().apply {
        entities.value = mapOf(
            "a" to testEntity("a", Kind.COVER, "Bay", area = "living", capabilities = Capabilities(position = true)),
            "b" to testEntity("b", Kind.COVER, "Side", area = "living", capabilities = Capabilities(position = true)),
            "c" to testEntity("c", Kind.COVER, "Patio", area = "living"),
        )
        areas.value = listOf(Area("living", "Living room"))
    }

    @Test
    fun `a group send reaches every available member`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.sendGroup("group:living:cover", Command.Close)
        assertEquals(setOf("a" to Command.Close, "b" to Command.Close, "c" to Command.Close), src.sent.toSet())
    }

    @Test
    fun `unavailable members are skipped`() = runTest(dispatcher) {
        val src = groupSource().apply { stateMap.value = mapOf("c" to EntityState("c", StateValue.Cover(CoverState.OPEN, null), available = false)) }
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.sendGroup("group:living:cover", Command.Open)
        assertEquals(setOf("a", "b"), src.sent.map { it.first }.toSet())
    }

    @Test
    fun `a stale member blocks the whole group send`() = runTest(dispatcher) {
        val src = groupSource().apply { stateMap.value = mapOf("a" to EntityState("a", StateValue.Cover(CoverState.OPEN, 100), stale = true)) }
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val event = async { vm.events.first() }
        vm.sendGroup("group:living:cover", Command.Open)
        assertEquals(HomeEvent.WaitingForConnection, event.await())
        assertTrue(src.sent.isEmpty())
    }

    @Test
    fun `only resends to the listed members`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.sendGroup("group:living:cover", Command.Open, only = listOf("b"))
        assertEquals(listOf<Pair<String, Command>>("b" to Command.Open), src.sent)
    }

    @Test
    fun `failures of one group send merge into one event`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.sendGroup("group:living:cover", Command.Close)
        src.commandFailures.emit(CommandFailure("a", Command.Close, "timeout"))
        src.commandFailures.emit(CommandFailure("c", Command.Close, "timeout"))
        advanceTimeBy(1_100)
        assertEquals(
            listOf<HomeEvent>(HomeEvent.GroupNotConfirmed("group:living:cover", Kind.COVER, listOf("a", "c"), listOf("Bay", "Patio"), Command.Close)),
            events,
        )
    }

    @Test
    fun `a failure for a member sent on its own stays a single NotConfirmed`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.sendGroup("group:living:cover", Command.Close)
        vm.send("a", Command.Open)
        src.commandFailures.emit(CommandFailure("a", Command.Open, "timeout"))
        advanceTimeBy(1_100)
        assertEquals(listOf<HomeEvent>(HomeEvent.NotConfirmed("a", Command.Open)), events)
    }

    @Test
    fun `the light group inline toggle sends to every light`() = runTest(dispatcher) {
        val src = FakeEntitySource().apply {
            entities.value = mapOf(
                "x" to testEntity("x", Kind.LIGHT, area = "living"),
                "y" to testEntity("y", Kind.LIGHT, area = "living"),
            )
            areas.value = listOf(Area("living", "Living room"))
            stateMap.value = mapOf("x" to EntityState("x", StateValue.Light(true, 255)))
        }
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val group = vm.state.value.cards.single()
        vm.sendGroup(group.id, GroupCommands.toggle(group))
        assertEquals(setOf("x" to Command.TurnOff, "y" to Command.TurnOff), src.sent.toSet())
    }

    @Test
    fun `a rejected group send reports once`() = runTest(dispatcher) {
        val src = groupSource().apply { result = CommandResult.Rejected("read-only") }
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.sendGroup("group:living:cover", Command.Open)
        assertEquals(listOf<HomeEvent>(HomeEvent.Rejected("group:living:cover", "read-only")), events)
    }

    @Test
    fun `customizing a group renames it and ungroups the unticked members`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.customizeGroup("group:living:cover", "Baie", favorite = false, removed = listOf("c"))
        assertEquals(mapOf("cover" to "Baie"), src.areas.value.single().groupNames)
        assertTrue(src.overrides.getValue("c").ungrouped)
        assertEquals(listOf("a", "b"), vm.state.value.cards.single { it.isGroup }.members.map { it.id })
    }

    @Test
    fun `failures of two different group sends never merge, so Retry repeats the right command`() = runTest(dispatcher) {
        val src = groupSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.sendGroup("group:living:cover", Command.Open)
        vm.sendGroup("group:living:cover", Command.SetPosition(30))
        src.commandFailures.emit(CommandFailure("c", Command.Open, "timeout"))
        advanceTimeBy(300)
        src.commandFailures.emit(CommandFailure("a", Command.SetPosition(30), "timeout"))
        src.commandFailures.emit(CommandFailure("b", Command.SetPosition(30), "timeout"))
        advanceTimeBy(1_100)
        assertEquals(
            setOf<HomeEvent>(
                HomeEvent.GroupNotConfirmed("group:living:cover", Kind.COVER, listOf("c"), listOf("Patio"), Command.Open),
                HomeEvent.GroupNotConfirmed("group:living:cover", Kind.COVER, listOf("a", "b"), listOf("Bay", "Side"), Command.SetPosition(30)),
            ),
            events.toSet(),
        )
    }

    /** [groupSource] plus a lone "den" room, selected so House does not show the living-room group. */
    private fun dashboardSource() = groupSource().apply {
        entities.value += "d" to testEntity("d", Kind.SWITCH, area = "den")
        areas.value += Area("den", "Den")
    }

    /** The living-room cover group card as another view (a dashboard) would hold it; House stays on Favourites. */
    private fun livingGroupCard(src: FakeEntitySource, stale: Boolean = false): CardUi {
        val states = if (stale) src.entities.value.keys.associateWith { EntityState(it, StateValue.Cover(CoverState.OPEN, 100), stale = true) } else emptyMap()
        return HomeUiBuilder.build(
            src.entities.value, states, src.areas.value, RoomFilter.Area("living"),
            LinkState.Connected(TransportMode.DEMO), java.util.Locale.US,
        ).cards.single { it.isGroup }
    }

    @Test
    fun `group send reaches members when House shows another filter`() = runTest(dispatcher) {
        val src = dashboardSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Area("den"))
        val card = livingGroupCard(src)
        assertEquals(null, vm.state.value.card(card.id))
        vm.sendGroup(card.id, Command.Close, group = card)
        assertEquals(setOf("a" to Command.Close, "b" to Command.Close, "c" to Command.Close), src.sent.toSet())
    }

    @Test
    fun `tapping a stale dashboard card that House does not show waits for connection`() = runTest(dispatcher) {
        val src = dashboardSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Area("living"))
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        val den = HomeUiBuilder.build(
            src.entities.value, mapOf("d" to EntityState("d", StateValue.Unknown, stale = true)), src.areas.value,
            RoomFilter.Area("den"), LinkState.Connected(TransportMode.DEMO), java.util.Locale.US,
        ).cards.single()
        assertEquals(null, vm.state.value.card("d"))
        vm.tap(den)
        assertTrue(src.sent.isEmpty())
        assertEquals(listOf<HomeEvent>(HomeEvent.WaitingForConnection), events)
    }

    @Test
    fun `retry without a card reuses the last group card given`() = runTest(dispatcher) {
        val src = dashboardSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Area("den"))
        val card = livingGroupCard(src)
        vm.sendGroup(card.id, Command.Close, group = card)
        src.sent.clear()
        vm.sendGroup(card.id, Command.Close, only = listOf("b"))
        assertEquals(listOf<Pair<String, Command>>("b" to Command.Close), src.sent)
    }

    @Test
    fun `a stale dashboard card waits for connection`() = runTest(dispatcher) {
        val src = dashboardSource()
        val vm = HomeViewModel(src, SavedStateHandle(), dispatcher, preferences = testPreferences())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Area("den"))
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        val staleGroup = livingGroupCard(src, stale = true)
        vm.sendGroup(staleGroup.id, Command.Open, group = staleGroup)
        val staleCard = staleGroup.members.first().copy(stale = true)
        vm.send("zzz", Command.Open, card = staleCard)
        assertTrue(src.sent.isEmpty())
        assertEquals(listOf<HomeEvent>(HomeEvent.WaitingForConnection, HomeEvent.WaitingForConnection), events)
    }

    private val livingZone = """{"state":"active","entities":["lamp"],"sensor":null}"""
    private val livingDecision =
        """{"zone":"living","mode":"heat","setpoint":20.0,"source":"schedule","reason":"","reading":19.4,"at":1,"entities":{"lamp":{}}}"""

    private fun climate() = ClimateRepository().apply {
        onRetained("living", ClimateDoc.SETTINGS, livingZone)
        onRetained("living", ClimateDoc.DECISION, livingDecision)
    }

    @Test
    fun `climat banner shows only in a room view`() = runTest(dispatcher) {
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences(), climate = climate())
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.All)
        assertEquals(null, vm.state.value.climat)
        vm.select(RoomFilter.Favorites)
        assertEquals(null, vm.state.value.climat)
        vm.select(RoomFilter.Area("kitchen"))
        assertEquals("living", vm.state.value.climat?.zone)
        assertEquals(19.4, vm.state.value.climat?.reading)
    }

    @Test
    fun `climat banner follows the zone documents`() = runTest(dispatcher) {
        val repo = climate()
        val vm = HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences(), climate = repo)
        backgroundScope.launch { vm.state.collect {} }
        vm.select(RoomFilter.Area("kitchen"))
        assertEquals(ClimatWhy.SCHEDULE, vm.state.value.climat?.why)
        repo.onRetained("living", ClimateDoc.SETTINGS, null)
        assertEquals(null, vm.state.value.climat)
    }

    private val boostOverride = """[{"entity":null,"setpoint":22.0,"mode":null,"until":9999999999,"origin":"user"}]"""

    private fun climatVm(repo: ClimateRepository, transport: FakeMqttTransport) =
        HomeViewModel(source, SavedStateHandle(), dispatcher, preferences = testPreferences(), climate = repo, transport = transport)

    @Test
    fun `startBoost publishes the hold command`() = runTest(dispatcher) {
        val transport = FakeMqttTransport()
        val vm = climatVm(climate(), transport)
        backgroundScope.launch { vm.state.collect {} }
        vm.startBoost("living", 22.0, 90)
        val sent = transport.published.single()
        assertEquals("nidoo/climate/zone/living/hold/set", sent.topic)
        assertEquals("""{"setpoint":22.0,"minutes":90}""", sent.payloadString())
        assertFalse(sent.retained)
        assertTrue(vm.state.value.climatSending)
    }

    @Test
    fun `cancelBoost publishes an empty payload`() = runTest(dispatcher) {
        val transport = FakeMqttTransport()
        val vm = climatVm(climate(), transport)
        vm.cancelBoost("living")
        assertEquals(listOf("nidoo/climate/zone/living/hold/set" to ""), transport.publishedStrings())
    }

    @Test
    fun `a boost is confirmed when the zone's overrides change`() = runTest(dispatcher) {
        val repo = climate()
        val vm = climatVm(repo, FakeMqttTransport())
        backgroundScope.launch { vm.state.collect {} }
        vm.startBoost("living", 22.0, 60)
        assertTrue(vm.state.value.climatSending)
        repo.onRetained("living", ClimateDoc.OVERRIDE, boostOverride)
        assertFalse(vm.state.value.climatSending)
        assertFalse(vm.state.value.climatError)
        advanceTimeBy(6_000)
        assertFalse(vm.state.value.climatError)
    }

    @Test
    fun `no reply from the hub is an error after five seconds`() = runTest(dispatcher) {
        val vm = climatVm(climate(), FakeMqttTransport())
        backgroundScope.launch { vm.state.collect {} }
        vm.startBoost("living", 22.0, 60)
        advanceTimeBy(4_900)
        assertTrue(vm.state.value.climatSending)
        assertFalse(vm.state.value.climatError)
        advanceTimeBy(200)
        assertFalse(vm.state.value.climatSending)
        assertTrue(vm.state.value.climatError)
        vm.dismissClimatError()
        assertFalse(vm.state.value.climatError)
    }

    @Test
    fun `each climat command reports its result once`() = runTest(dispatcher) {
        val repo = climate()
        val vm = climatVm(repo, FakeMqttTransport())
        val results = mutableListOf<ClimatResult>()
        backgroundScope.launch { vm.climatResults.collect { results += it } }
        vm.startBoost("living", 22.0, 60)
        repo.onRetained("living", ClimateDoc.OVERRIDE, boostOverride)
        assertEquals(listOf(ClimatResult.CONFIRMED), results)
        vm.cancelBoost("living")
        advanceTimeBy(5_100)
        assertEquals(listOf(ClimatResult.CONFIRMED, ClimatResult.REFUSED), results)
    }

    @Test
    fun `a failed publish is an error at once`() = runTest(dispatcher) {
        val transport = FakeMqttTransport().apply { failPublishes = true }
        val vm = climatVm(climate(), transport)
        backgroundScope.launch { vm.state.collect {} }
        vm.startBoost("living", 22.0, 60)
        assertFalse(vm.state.value.climatSending)
        assertTrue(vm.state.value.climatError)
    }
}

private fun testPreferences(): AppPreferences =
    AppPreferences.create(File.createTempFile("prefs", ".json").also { it.delete() }, CoroutineScope(Dispatchers.Unconfined))
