package com.gekkotron.nidoo.shared

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.connection.ConnectionTester
import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.data.AppPreferences
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.ProfileState
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.feature.onboarding.di.onboardingModule
import com.gekkotron.nidoo.shared.di.sharedModule
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.testing.FakeMqttTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import okio.FileSystem
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.assertEquals

/** The iOS shell, run on Robolectric: what the user sees across "Set up again". */
@RunWith(AndroidJUnit4::class)
class NidooAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-app-${Random.nextLong().toULong()}")
    private val sourcesBuilt = AtomicInteger()

    @After
    fun tearDown() {
        scope.cancel()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private object PlainSecrets : SecretStore {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(cipher: ByteArray) = cipher
    }

    /** What the iOS module provides, with fakes for the links. */
    private val platform = module {
        single { ProfileRepository.create(dir.resolve(ProfileRepository.FILE), PlainSecrets, scope) }
        single { AppPreferences.create(dir.resolve(AppPreferences.FILE), scope) }
        single { EntitySourceSession(FakeMqttTransport(), scope) }
        factory<EntitySource> {
            sourcesBuilt.incrementAndGet()
            get<EntitySourceSession>().current((get<ProfileRepository>().state.value as? ProfileState.Ready)?.profile)
        }
        single { ConnectionTester { emptyFlow() } }
    }

    private fun showApp() {
        val koin = koinApplication { modules(platform, onboardingModule, sharedModule) }
        compose.setContent { KoinIsolatedContext(koin) { NidooApp() } }
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun setUpAgainAfterTheDemoShowsAWorkingWelcome() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitFor("Set up again")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")

        compose.onNodeWithText("Scan setup code").assertIsEnabled()
        compose.onNodeWithText("Enter manually").assertIsEnabled()
        compose.onNodeWithText("Try the demo").assertIsEnabled()
    }

    @Test
    fun aNewHouseGetsANewSource() {
        showApp()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitFor("Set up again")
        compose.onNodeWithText("Set up again").performClick()
        waitFor("Try the demo")
        compose.onNodeWithText("Try the demo").performClick()
        waitFor("Set up again")

        assertEquals(2, sourcesBuilt.get())
    }
}
