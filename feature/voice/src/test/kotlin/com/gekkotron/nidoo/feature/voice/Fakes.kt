package com.gekkotron.nidoo.feature.voice

import com.gekkotron.nidoo.assist.AssistClient
import com.gekkotron.nidoo.assist.AssistReply
import com.gekkotron.nidoo.assist.AssistResult
import kotlinx.coroutines.CompletableDeferred

class FakeSpeechCapture : SpeechCapture {
    private var cb: SpeechCapture.Callbacks? = null
    private var pending: String? = null
    var starts = 0
        private set
    override fun start(callbacks: SpeechCapture.Callbacks) {
        starts++
        cb = callbacks
        pending?.let { emitFinal(it); pending = null }
    }
    override fun cancel() { cb = null }
    fun scheduleFinal(text: String) { pending = text }
    fun emitPartial(text: String) { cb?.onPartial(text) }
    fun emitFinal(text: String) { cb?.onFinal(text) }
    fun emitError(kind: SpeechError) { cb?.onError(kind) }
}

class FakeAssistClient : AssistClient {
    val results = ArrayDeque<AssistResult>()
    val sent = mutableListOf<String>()
    val answers = mutableListOf<AssistReply>()
    val speechRequests = mutableListOf<String>()
    val speechResults = ArrayDeque<ByteArray?>()
    /** When set, [send] and [answer] suspend until it completes. */
    var gate: CompletableDeferred<Unit>? = null
    /** When set, [speech] suspends until it completes. */
    var speechGate: CompletableDeferred<Unit>? = null

    override suspend fun send(text: String): AssistResult {
        sent += text
        gate?.await()
        return results.removeFirst()
    }

    override suspend fun answer(reply: AssistReply): AssistResult {
        answers += reply
        gate?.await()
        return results.removeFirst()
    }

    override suspend fun speech(text: String): ByteArray? {
        speechRequests += text
        speechGate?.await()
        return speechResults.removeFirstOrNull()
    }
}

class FakePlayer : AudioPlayer {
    val played = mutableListOf<ByteArray>()
    var stops = 0
        private set
    private var finish: CompletableDeferred<Boolean>? = null

    override suspend fun play(wav: ByteArray): Boolean {
        played += wav
        val d = CompletableDeferred<Boolean>()
        finish = d
        return d.await()
    }

    override fun stop() { stops++ }

    /** Ends the current playback as if the audio reached its end. */
    fun finish() { finish?.complete(true) }
}
