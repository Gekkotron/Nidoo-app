package com.gekkotron.nidoo.feature.controls.android

import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import androidx.annotation.RequiresApi
import com.gekkotron.nidoo.feature.controls.ControlsComponentProvider
import com.gekkotron.nidoo.feature.controls.ControlsSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.jdk9.asPublisher
import java.util.concurrent.Flow
import java.util.function.Consumer
import com.gekkotron.nidoo.feature.controls.model.ControlAction as Action

@RequiresApi(30)
class NidooControlsService : ControlsProviderService() {
    private lateinit var session: ControlsSession
    private lateinit var factory: ControlFactory
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        session = (applicationContext as ControlsComponentProvider).controlsComponent().session
        factory = ControlFactory(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> =
        flow { session.catalog().forEach { emit(factory.stateless(it)) } }.asPublisher(scope.coroutineContext)

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> =
        session.watch(controlIds.toSet()).map(factory::stateful).asPublisher(scope.coroutineContext)

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        val mapped = when (action) {
            is BooleanAction -> Action.SetOn(action.newState)
            is FloatAction -> Action.SetBrightness(action.newValue)
            else -> null
        }
        val ok = mapped != null && session.perform(controlId, mapped)
        consumer.accept(if (ok) ControlAction.RESPONSE_OK else ControlAction.RESPONSE_FAIL)
    }
}
