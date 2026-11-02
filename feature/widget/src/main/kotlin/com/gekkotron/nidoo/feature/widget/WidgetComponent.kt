package com.gekkotron.nidoo.feature.widget

import com.gekkotron.nidoo.feature.widget.store.WidgetConfigStore
import com.gekkotron.nidoo.feature.widget.store.WidgetSnapshotStore
import com.gekkotron.nidoo.feature.widget.work.WidgetScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** What the widget's Android entry points need; `NidooApplication` hands it out (same pattern as `PushComponent`). */
class WidgetComponent(
    val entities: WidgetEntities,
    val configs: WidgetConfigStore,
    val snapshots: WidgetSnapshotStore,
    val scheduler: WidgetScheduler,
    val refresher: WidgetRefresher,
    /** Whether the opt-in voice assistant is on; the widget shows its mic only then. */
    val voiceEnabled: Flow<Boolean> = flowOf(false),
)

interface WidgetComponentProvider {
    fun widgetComponent(): WidgetComponent
}
