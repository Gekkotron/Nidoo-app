package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.demo.DemoBroker
import com.gekkotron.nidoo.source.MqttDiscoverySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs the demo house through the shared transport and discovery code, as the app will: [DemoBroker] replays
 * its retained configs and [MqttDiscoverySource] builds the entities. Returns once discovery has been quiet for
 * a moment, and stops both before returning.
 */
@OptIn(FlowPreview::class)
suspend fun demoHouseSummary(scope: CoroutineScope): String {
    val job = Job(scope.coroutineContext[Job])
    val house = CoroutineScope(scope.coroutineContext + job)
    try {
        val source = MqttDiscoverySource(DemoBroker(house), house)
        val entities = source.entities.filter { it.isNotEmpty() }.debounce(SETTLE).first()
        return "Demo house: ${entities.size} entities"
    } finally {
        job.cancel()
    }
}

private val SETTLE = 500.milliseconds
