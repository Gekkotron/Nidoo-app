package com.gekkotron.nidoo.di

import com.gekkotron.nidoo.connection.EntitySourceSession
import com.gekkotron.nidoo.feature.controls.ControlsComponent
import com.gekkotron.nidoo.feature.controls.ControlsSession
import com.gekkotron.nidoo.feature.widget.RefreshResult
import com.gekkotron.nidoo.feature.widget.WidgetEntities
import com.gekkotron.nidoo.source.MqttDiscoverySource
import com.gekkotron.nidoo.transport.ActiveTransport
import com.gekkotron.nidoo.transport.LinkState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import javax.inject.Singleton

/** Android Device controls (lights and switches), served from the same door as the widget. */
@Module
@InstallIn(SingletonComponent::class)
object ControlsModule {
    @Provides
    @Singleton
    fun controlsComponent(
        entities: WidgetEntities,
        session: EntitySourceSession,
        active: ActiveTransport,
        @ApplicationScope scope: CoroutineScope,
    ): ControlsComponent = ControlsComponent(
        ControlsSession(
            entities = entities,
            live = {
                if (active.link.value is LinkState.Connected) {
                    session.peek()?.let { s ->
                        combine(s.entities, s.areas, s.states()) { e, a, st ->
                            RefreshResult.Ok(
                                entities = e,
                                states = st,
                                complete = (s as? MqttDiscoverySource)?.hasSettled?.value ?: true,
                                historyEnabled = false,
                                areas = a,
                            )
                        }
                    }
                } else {
                    null
                }
            },
            scope = scope,
        ),
    )
}
