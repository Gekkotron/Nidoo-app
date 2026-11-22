package com.gekkotron.nidoo.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DemoHouseViewModelTest {
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun summaryStartsEmptyThenShowsTheDemoHouse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = DemoHouseViewModel(summary = { "Demo house: 3 entities" })
        assertNull(vm.summary.value)
        advanceUntilIdle()
        assertEquals("Demo house: 3 entities", vm.summary.value)
    }
}
