package com.gekkotron.nidoo.shared.tabs

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.model.AlarmState
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.shared.FakeEntitySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object FakeBindings : Bindings

@OptIn(ExperimentalCoroutinesApi::class)
class MainTabsViewModelTest {
    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
    }

    private fun armedSource() = FakeEntitySource().apply {
        entities.value = mapOf("alarm" to Entity("alarm", null, "alarm", Kind.ALARM_PANEL, null, null, null, Capabilities(), FakeBindings))
        stateMap.value = mapOf("alarm" to EntityState("alarm", StateValue.Alarm(AlarmState.ARMED_AWAY)))
    }

    @Test
    fun statusFollowsTheProfilesSource() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val a = ProfileState.Ready(ConnectionProfile.Demo)
        val b = ProfileState.Ready(ConnectionProfile.Direct(LocalBroker("hub.local")))
        val profiles = MutableStateFlow<ProfileState>(a)
        val armed = armedSource()
        val quiet = FakeEntitySource()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { MainTabsViewModel(profiles) { if (profiles.value == a) armed else quiet } } },
        )[MainTabsViewModel::class]

        val collecting: Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.status.collect { } }
        assertTrue(vm.status.value.alarmArmed)

        profiles.value = b
        assertFalse(vm.status.value.alarmArmed)

        collecting.cancel()
        store.clear()
    }

    @Test
    fun anEqualProfileAfterNoneAsksForTheSourceAgain() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val a = ProfileState.Ready(ConnectionProfile.Demo)
        val profiles = MutableStateFlow<ProfileState>(a)
        var calls = 0
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { MainTabsViewModel(profiles) { calls++; armedSource() } } },
        )[MainTabsViewModel::class]

        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.status.collect { } }
        assertEquals(1, calls)
        assertTrue(vm.status.value.alarmArmed)

        profiles.value = ProfileState.None
        assertEquals(1, calls)
        assertEquals(TabStatus(), vm.status.value)

        profiles.value = a
        assertEquals(2, calls)
        assertTrue(vm.status.value.alarmArmed)

        collecting.cancel()
        store.clear()
    }

    @Test
    fun loadingAsTheFirstValueNeverAsksForASource() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val profiles = MutableStateFlow<ProfileState>(ProfileState.Loading)
        var calls = 0
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { MainTabsViewModel(profiles) { calls++; armedSource() } } },
        )[MainTabsViewModel::class]

        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.status.collect { } }
        assertEquals(0, calls)
        assertEquals(TabStatus(), vm.status.value)

        collecting.cancel()
        store.clear()
    }
}
