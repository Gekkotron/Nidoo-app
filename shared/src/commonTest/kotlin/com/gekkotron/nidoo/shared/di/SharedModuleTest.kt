package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.shared.DemoHouseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame

@OptIn(ExperimentalCoroutinesApi::class)
class SharedModuleTest {
    // The ViewModel starts the demo house on Dispatchers.Main; a test dispatcher that never runs keeps it idle.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sharedModuleResolvesTheRootViewModel() {
        val koin = koinApplication { modules(sharedModule) }.koin
        // A ViewModel definition hands out a new instance per request (the owner keeps it).
        assertNotSame(koin.get<DemoHouseViewModel>(), koin.get<DemoHouseViewModel>())
    }
}
