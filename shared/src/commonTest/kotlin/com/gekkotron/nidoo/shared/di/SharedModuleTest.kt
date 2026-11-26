package com.gekkotron.nidoo.shared.di

import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.shared.AppShellViewModel
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedModuleTest {
    private val scope = CoroutineScope(Job())
    private val viewModels = mutableListOf<ViewModel>()
    private var koinApp: KoinApplication? = null
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-shared-${Random.nextLong().toULong()}")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        // Stop everything that can still run before Main goes away and the directory is deleted.
        viewModels.forEach { it.viewModelScope.cancel() }
        koinApp?.close()
        scope.cancel()
        Dispatchers.resetMain()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private object PlainSecrets : SecretStore {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(cipher: ByteArray) = cipher
    }

    /** What each platform module provides (the iOS module, Hilt's bridge on Android). */
    private val platform = module {
        single { ProfileRepository.create(dir.resolve(ProfileRepository.FILE), PlainSecrets, scope) }
        single { AppPreferences.create(dir.resolve(AppPreferences.FILE), scope) }
        single { EntitySourceSession(FakeMqttTransport(), scope) }
        factory<EntitySource> { get<EntitySourceSession>().current(null) }
    }

    @Test
    fun sharedModuleResolvesItsViewModels() {
        val app = koinApplication { modules(platform, sharedModule) }.also { koinApp = it }
        viewModels += app.koin.get<AppShellViewModel>()
    }
}
