package com.gekkotron.nidoo.feature.controls.android

import android.app.Application
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import com.gekkotron.nidoo.feature.controls.ControlsComponent
import com.gekkotron.nidoo.feature.controls.ControlsComponentProvider
import com.gekkotron.nidoo.feature.controls.ControlsSession
import com.gekkotron.nidoo.feature.widget.CatalogResult
import com.gekkotron.nidoo.feature.widget.Failure
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.SendResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.model.Bindings
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import com.gekkotron.nidoo.model.UiOverride
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

private object NoBindings : Bindings

private class FakeEntities : WidgetEntities {
    val sent = CopyOnWriteArrayList<Pair<String, Command>>()
    val gate = CompletableDeferred<SendResult>()
    var refreshResult: RefreshResult = RefreshResult.Failed(Failure.OFFLINE)

    override suspend fun refresh(ids: Set<String>) = refreshResult

    override suspend fun send(id: String, command: Command): SendResult {
        sent += id to command
        return gate.await()
    }

    override suspend fun catalog(): CatalogResult = CatalogResult.Failed(Failure.OFFLINE)
}

class TestControlsApp : Application(), ControlsComponentProvider {
    override fun controlsComponent(): ControlsComponent = component ?: error("not set")

    companion object {
        var component: ControlsComponent? = null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestControlsApp::class)
class NidooControlsServiceTest {
    private val fake = FakeEntities()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var session: ControlsSession
    private lateinit var service: NidooControlsService

    @Before
    fun setUp() {
        session = ControlsSession(fake, { null }, scope)
        TestControlsApp.component = ControlsComponent(session)
        service = Robolectric.buildService(NidooControlsService::class.java).create().get()
    }

    @After
    fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        TestControlsApp.component = null
    }

    private fun known(id: String) {
        val entity = Entity(id, null, "Lamp", Kind.LIGHT, null, null, null, Capabilities(brightness = true), NoBindings, UNASSIGNED_AREA, UiOverride(), null, protocolKind = Kind.LIGHT)
        fake.refreshResult = RefreshResult.Ok(
            mapOf(id to entity),
            mapOf(id to EntityState(id, StateValue.Light(false, null))),
            complete = true,
            historyEnabled = false,
            areas = emptyList(),
        )
        runBlocking { withTimeout(5_000) { session.watch(setOf(id)).first() } }
    }

    private fun awaitSent(): Pair<String, Command> = runBlocking {
        withTimeout(5_000) {
            while (fake.sent.isEmpty()) kotlinx.coroutines.delay(10)
            fake.sent.first()
        }
    }

    @Test
    fun boolean_action_answers_ok_immediately_and_sends_turn_on() {
        known("l1")
        val answers = mutableListOf<Int>()
        service.performControlAction("l1", BooleanAction("l1_toggle", true)) { answers += it }
        assertEquals(listOf(ControlAction.RESPONSE_OK), answers)
        assertTrue(!fake.gate.isCompleted)
        assertEquals("l1" to Command.TurnOn(), awaitSent())
    }

    @Test
    fun float_action_sends_brightness() {
        known("l1")
        val answers = mutableListOf<Int>()
        service.performControlAction("l1", FloatAction("l1_range", 50f)) { answers += it }
        assertEquals(listOf(ControlAction.RESPONSE_OK), answers)
        assertEquals("l1" to Command.TurnOn(brightness = 128), awaitSent())
    }

    @Test
    fun unknown_id_answers_fail() {
        val answers = mutableListOf<Int>()
        service.performControlAction("nope", BooleanAction("nope_toggle", true)) { answers += it }
        assertEquals(listOf(ControlAction.RESPONSE_FAIL), answers)
    }
}
