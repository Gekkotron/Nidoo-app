package com.gekkotron.nidoo.feature.controls

import com.gekkotron.nidoo.feature.controls.model.ControlAction
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.widget.CatalogResult
import com.gekkotron.nidoo.feature.widget.Failure
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.SendResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.model.Area
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlsSessionTest {
    private object NoBindings : Bindings

    private fun entity(id: String, kind: Kind = Kind.LIGHT, area: String = UNASSIGNED_AREA, name: String = "Lamp $id", brightness: Boolean = true) =
        Entity(id, null, name, kind, null, null, null, Capabilities(brightness = brightness), NoBindings, area, UiOverride(), null, protocolKind = kind)

    private fun light(id: String, on: Boolean, brightness: Int?) = EntityState(id, StateValue.Light(on, brightness))

    private fun ok(entities: List<Entity>, states: List<EntityState> = emptyList(), complete: Boolean = true, areas: List<Area> = emptyList()) =
        RefreshResult.Ok(entities.associateBy { it.uniqueId }, states.associateBy { it.uniqueId }, complete, historyEnabled = false, areas = areas)

    private class Sent(val id: String, val command: Command, val result: CompletableDeferred<SendResult> = CompletableDeferred())

    private class FakeEntities : WidgetEntities {
        var refreshResult: RefreshResult = RefreshResult.Failed(Failure.OFFLINE)
        var catalogResult: CatalogResult = CatalogResult.Failed(Failure.OFFLINE)
        val sent = mutableListOf<Sent>()
        var catalogCalls = 0

        /** When set, refresh suspends until it completes (a slow cold session). */
        var refreshGate: CompletableDeferred<Unit>? = null

        override suspend fun refresh(ids: Set<String>): RefreshResult {
            refreshGate?.await()
            return refreshResult
        }

        override suspend fun send(id: String, command: Command): SendResult {
            val call = Sent(id, command)
            sent += call
            return call.result.await()
        }

        override suspend fun catalog(): CatalogResult {
            catalogCalls++
            return catalogResult
        }
    }

    private fun TestScope.session(fake: FakeEntities, live: () -> Flow<RefreshResult.Ok>? = { null }) =
        ControlsSession(fake, live, backgroundScope)

    private fun TestScope.collect(session: ControlsSession, vararg ids: String): MutableList<ControlModel> {
        val out = mutableListOf<ControlModel>()
        backgroundScope.launch { session.watch(ids.toSet()).collect { out += it } }
        runCurrent()
        return out
    }

    @Test
    fun catalog_lists_only_eligible_entities_with_zones() = runTest {
        val fake = FakeEntities()
        fake.catalogResult = CatalogResult.Ok(
            listOf(entity("l", area = "salon"), entity("s", Kind.SWITCH), entity("c", Kind.COVER)),
            listOf(Area("salon", "Salon")),
        )
        val models = session(fake).catalog()
        assertEquals(2, models.size)
        assertEquals("Salon", models.first { it.id == "l" }.zone)
    }

    @Test
    fun watch_emits_refreshed_state() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = ok(listOf(entity("l")), listOf(light("l", true, 255)))
        val out = collect(session(fake), "l")
        assertEquals(1, out.size)
        assertTrue(out[0].on)
        assertEquals(100, out[0].brightnessPercent)
        assertEquals(ControlStatus.OK, out[0].status)
    }

    @Test
    fun missing_id_is_not_found_only_when_discovery_settled() = runTest {
        val fake = FakeEntities()
        val session = session(fake)
        fake.refreshResult = ok(emptyList(), complete = true)
        val first = collect(session, "x")
        assertEquals(ControlStatus.NOT_FOUND, first.single().status)

        fake.refreshResult = ok(listOf(entity("l")), listOf(light("l", true, 10)))
        collect(session, "l")
        fake.refreshResult = ok(emptyList(), complete = false)
        val again = collect(session, "l")
        assertEquals(ControlStatus.UNAVAILABLE, again.last().status)
    }

    @Test
    fun refresh_failure_falls_back_to_catalog_names_with_failure_status() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = RefreshResult.Failed(Failure.OFFLINE)
        fake.catalogResult = CatalogResult.Ok(listOf(entity("l", name = "Desk")), emptyList())
        val out = collect(session(fake), "l")
        assertEquals(1, out.size)
        assertEquals("Desk", out[0].title)
        assertEquals(ControlStatus.OFFLINE, out[0].status)
    }

    @Test
    fun perform_on_unknown_id_returns_false() = runTest {
        assertFalse(session(FakeEntities()).perform("nope", ControlAction.SetOn(true)))
    }

    @Test
    fun perform_emits_the_confirmed_state() = runTest {
        val fake = FakeEntities()
        val e = entity("l")
        fake.refreshResult = ok(listOf(e), listOf(light("l", false, 0)))
        val session = session(fake)
        val out = collect(session, "l")
        assertTrue(session.perform("l", ControlAction.SetOn(true)))
        runCurrent()
        assertEquals(Command.TurnOn(), fake.sent.single().command)
        fake.sent.single().result.complete(SendResult.Confirmed(e, light("l", true, 128), historyEnabled = false))
        runCurrent()
        assertTrue(out.last().on)
        assertEquals(50, out.last().brightnessPercent)
    }

    @Test
    fun unconfirmed_send_shows_no_response() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = ok(listOf(entity("l")), listOf(light("l", false, 0)))
        val session = session(fake)
        val out = collect(session, "l")
        session.perform("l", ControlAction.SetOn(true))
        runCurrent()
        fake.sent.single().result.complete(SendResult.NotConfirmed)
        runCurrent()
        assertEquals(ControlStatus.NO_RESPONSE, out.last().status)
    }

    @Test
    fun latest_action_wins_when_sends_overlap() = runTest {
        val fake = FakeEntities()
        val e = entity("l")
        fake.refreshResult = ok(listOf(e), listOf(light("l", false, 0)))
        val session = session(fake)
        val out = collect(session, "l")
        val before = out.size
        session.perform("l", ControlAction.SetBrightness(20f))
        runCurrent()
        session.perform("l", ControlAction.SetBrightness(80f))
        runCurrent()
        assertEquals(listOf(Command.TurnOn(brightness = 51), Command.TurnOn(brightness = 204)), fake.sent.map { it.command })
        assertTrue(fake.sent[0].result.isCancelled || !fake.sent[0].result.isCompleted)
        fake.sent[1].result.complete(SendResult.Confirmed(e, light("l", true, 204), historyEnabled = false))
        runCurrent()
        assertEquals(before + 1, out.size)
        assertEquals(80, out.last().brightnessPercent)
    }

    @Test
    fun live_updates_are_forwarded_while_watching() = runTest {
        val fake = FakeEntities()
        val e = entity("l")
        fake.refreshResult = ok(listOf(e), listOf(light("l", false, 0)))
        val live = MutableStateFlow(ok(listOf(e), listOf(light("l", false, 0))))
        val out = collect(session(fake) { live }, "l")
        live.value = ok(listOf(e), listOf(light("l", true, 255)))
        runCurrent()
        assertTrue(out.last().on)
        assertEquals(100, out.last().brightnessPercent)
    }

    @Test
    fun offline_with_no_catalog_still_shows_a_tile() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = RefreshResult.Failed(Failure.OFFLINE)
        fake.catalogResult = CatalogResult.Failed(Failure.OFFLINE)
        val out = collect(session(fake), "l")
        assertEquals("l", out.single().id)
        assertEquals(ControlStatus.OFFLINE, out.single().status)
    }

    @Test
    fun catalog_fallback_runs_once_for_many_ids() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = RefreshResult.Failed(Failure.OFFLINE)
        fake.catalogResult = CatalogResult.Ok(listOf(entity("a"), entity("b"), entity("c")), emptyList())
        val out = collect(session(fake), "a", "b", "c")
        assertEquals(3, out.size)
        assertEquals(1, fake.catalogCalls)
    }

    @Test
    fun never_seen_missing_id_while_settling_shows_unavailable() = runTest {
        val fake = FakeEntities()
        fake.refreshResult = ok(emptyList(), complete = false)
        val out = collect(session(fake), "x")
        assertEquals(ControlStatus.UNAVAILABLE, out.single().status)
    }

    @Test
    fun a_refresh_that_lands_after_a_send_does_not_revert_it() = runTest {
        val fake = FakeEntities()
        val e = entity("l")
        fake.refreshGate = CompletableDeferred()
        fake.refreshResult = ok(listOf(e), listOf(light("l", false, 0)))
        val session = session(fake)
        val out = collect(session, "l")
        // Tapped while the cold refresh is still running: accepted, not refused.
        assertTrue(session.perform("l", ControlAction.SetOn(true)))
        runCurrent()
        fake.sent.single().result.complete(SendResult.Confirmed(e, light("l", true, 255), historyEnabled = false))
        runCurrent()
        assertTrue(out.last().on)
        fake.refreshGate!!.complete(Unit)
        runCurrent()
        assertTrue(out.last().on)
    }

    @Test
    fun identical_live_updates_are_not_resent() = runTest {
        val fake = FakeEntities()
        val e = entity("l")
        val other = entity("o")
        fake.refreshResult = ok(listOf(e), listOf(light("l", true, 255)))
        val live = MutableStateFlow(ok(listOf(e, other), listOf(light("l", true, 255), light("o", false, 0))))
        val out = collect(session(fake) { live }, "l")
        val before = out.size
        live.value = ok(listOf(e, other), listOf(light("l", true, 255), light("o", true, 255)))
        runCurrent()
        assertEquals(before, out.size)
    }

    @Test
    fun cancelled_watch_stops_listening() = runTest {
        val fake = FakeEntities()
        fake.refreshGate = CompletableDeferred()
        val live = MutableStateFlow(ok(emptyList()))
        val session = session(fake) { live }
        val job = backgroundScope.launch { session.watch(setOf("x")).collect {} }
        runCurrent()
        job.cancel()
        runCurrent()
        // No longer watched and never seen: a tap is refused.
        assertFalse(session.perform("x", ControlAction.SetOn(true)))
        assertEquals(0, live.subscriptionCount.value)
    }
}
