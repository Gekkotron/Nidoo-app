package com.gekkotron.nidoo.feature.voice

import com.gekkotron.nidoo.model.CommandResult
import com.gekkotron.nidoo.source.EntitySource
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.voice.Answer
import com.gekkotron.nidoo.voice.AnswerFormatter
import com.gekkotron.nidoo.voice.CommandMapper
import com.gekkotron.nidoo.voice.VoiceCatalog
import com.gekkotron.nidoo.voice.VoiceIntent
import com.gekkotron.nidoo.voice.VoiceSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

data class QueryResult(val answer: Answer)

class MqttDownException : RuntimeException("mqtt is not connected")

interface CommandRunner {
    suspend fun run(intent: VoiceIntent, catalog: VoiceCatalog, code: String? = null, source: VoiceSource = VoiceSource.RULES): ExecutionResult
    suspend fun query(intent: VoiceIntent, catalog: VoiceCatalog): QueryResult
}

/**
 * Resolves the [EntitySource] lazily on every [run] so a stale source (e.g. a surrogate built during
 * a cold voice-first start, before the real profile loaded) is never captured for the process lifetime.
 */
open class VoiceCommandRunner(private val entitySource: () -> EntitySource) : CommandRunner {
    override suspend fun run(intent: VoiceIntent, catalog: VoiceCatalog, code: String?, source: VoiceSource): ExecutionResult = coroutineScope {
        val current = entitySource()
        if (current.link.value !is LinkState.Connected) throw MqttDownException()
        val commands = CommandMapper.map(intent, catalog, code)
        val results = commands.map { (id, cmd) ->
            async {
                val r = current.send(id, cmd)
                id to r
            }
        }.awaitAll()
        val succeeded = results.filter { it.second is CommandResult.Accepted }.map { it.first }
        val failed = results.mapNotNull { (id, r) -> if (r is CommandResult.Rejected) id to r.reason else null }
        ExecutionResult(intent, source, succeeded, failed)
    }

    override suspend fun query(intent: VoiceIntent, catalog: VoiceCatalog): QueryResult {
        val snapshot = entitySource().states().first()
        return QueryResult(AnswerFormatter.format(catalog.lang, intent, snapshot, catalog))
    }
}
