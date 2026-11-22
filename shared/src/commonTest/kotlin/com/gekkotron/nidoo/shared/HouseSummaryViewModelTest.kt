package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.CommandFailure
import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.model.Device
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.UiOverride
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HouseSummaryViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSource : EntitySource {
        override val link = MutableStateFlow<LinkState>(LinkState.Connecting)
        override val entities = MutableStateFlow<Map<String, Entity>>(emptyMap())
        override val devices = MutableStateFlow<Map<String, Device>>(emptyMap())
        override val areas = MutableStateFlow<List<Area>>(emptyList())
        override val commandFailures: SharedFlow<CommandFailure> = MutableSharedFlow()
        override fun states(): Flow<Map<String, EntityState>> = emptyFlow()
        override suspend fun send(uniqueId: String, command: Command): CommandResult = error("unused")
        override suspend fun setOverride(uniqueId: String, override: UiOverride) = Unit
        override suspend fun setAreas(areas: List<Area>) = Unit
    }

    private fun entity(id: String, deviceId: String?) =
        Entity(id, deviceId, id, Kind.SWITCH, null, null, null, Capabilities(), object : Bindings {})

    private fun device(id: String) = Device(id, id, null, null, null)

    @Test
    fun countsDevicesAndLooseEntities() = runTest {
        val source = FakeSource()
        val vm = HouseSummaryViewModel(source)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.summary.collect {} }
        source.devices.value = mapOf("d1" to device("d1"), "d2" to device("d2"))
        source.entities.value = mapOf("a" to entity("a", "d1"), "b" to entity("b", "d2"), "c" to entity("c", null))
        assertEquals(3, vm.summary.value.devices)
    }

    @Test
    fun followsTheLink() = runTest {
        val source = FakeSource()
        val vm = HouseSummaryViewModel(source)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.summary.collect {} }
        source.link.value = LinkState.Connected(TransportMode.GATEWAY)
        assertEquals(LinkState.Connected(TransportMode.GATEWAY), vm.summary.value.link)
    }
}
