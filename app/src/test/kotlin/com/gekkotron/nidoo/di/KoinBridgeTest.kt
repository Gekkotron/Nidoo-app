package com.gekkotron.nidoo.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.data.create
import com.gekkotron.nidoo.source.EntitySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class KoinBridgeTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob())
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        stopKoin()
        scope.cancel()
    }

    private inline fun <reified T> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("unused") } as T

    /** A Hilt stand-in: fixed profiles and preferences; EntitySource is unscoped in Hilt, so a new one per call. */
    private inner class FakeBridge : KoinBridgeEntryPoint {
        val profiles = ProfileRepository.create(folder.newFile(), stub<SecretStore>(), scope)
        val preferences = AppPreferences.create(folder.newFile(), scope)
        var sourcesHandedOut = 0
        override fun profiles() = profiles
        override fun preferences() = preferences
        override fun entitySource(): EntitySource = stub<EntitySource>().also { sourcesHandedOut++ }
    }

    @Test
    fun bridgeHandsOutHiltsInstances() {
        val hilt = FakeBridge()
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        assertSame(hilt.profiles, koin.get<ProfileRepository>())
        assertSame(hilt.preferences, koin.get<AppPreferences>())
    }

    @Test
    fun entitySourceIsAskedOfHiltEachTime() {
        val hilt = FakeBridge()
        startNidooKoin(app, hilt)
        val koin = GlobalContext.get()
        assertNotSame(koin.get<EntitySource>(), koin.get<EntitySource>())
        assertEquals(2, hilt.sourcesHandedOut)
    }

    @Test
    fun startingTwiceReplacesTheGraph() {
        val first = FakeBridge()
        val second = FakeBridge()
        startNidooKoin(app, first)
        startNidooKoin(app, second)
        assertSame(second.preferences, GlobalContext.get().get<AppPreferences>())
    }
}
