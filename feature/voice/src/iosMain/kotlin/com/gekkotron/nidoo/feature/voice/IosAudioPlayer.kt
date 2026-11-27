package com.gekkotron.nidoo.feature.voice

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** As on Android: a player that never reports the end must not hold the sheet open past the audio's length + 2 s. */
private const val END_GRACE_MS = 2_000L

/**
 * Plays the hub's WAV with AVAudioPlayer, in whatever audio session listening set up (PlayAndRecord, DefaultToSpeaker).
 * Like the voice session that drives it, it is used from the main thread, where AVAudioPlayer reports the end.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {
    private var finished: (() -> Unit)? = null

    /** AVAudioPlayer holds its delegate weakly: this keeps it alive while the audio plays. */
    private var delegate: FinishDelegate? = null

    /** The player of the call in flight, so [stop] silences it at once instead of when `play` unwinds. */
    private var current: AVAudioPlayer? = null

    override suspend fun play(wav: ByteArray): Boolean {
        if (wav.isEmpty()) return false
        val player = orNilInit { AVAudioPlayer(data = wav.toNSData(), error = null) } ?: return false
        if (!player.prepareToPlay()) return false
        var playing = false
        var ownResume: (() -> Unit)? = null
        var ownDelegate: FinishDelegate? = null
        try {
            withTimeoutOrNull((player.duration * 1000).toLong() + END_GRACE_MS) {
                suspendCancellableCoroutine { cont ->
                    val resume = { if (cont.isActive) cont.resume(Unit) }
                    finished = resume
                    ownResume = resume
                    current = player
                    ownDelegate = FinishDelegate(resume).also { player.delegate = it }
                    delegate = ownDelegate
                    playing = player.play()
                    if (!playing) resume()
                }
            }
        } finally {
            player.delegate = null
            player.stop()
            // A repeat() may already have started the next play: only clear what is still this call's own.
            if (finished === ownResume) finished = null
            if (delegate === ownDelegate) delegate = null
            if (current === player) current = null
        }
        return playing
    }

    override fun stop() {
        current?.stop()
        finished?.invoke()
    }
}

private class FinishDelegate(private val onFinish: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onFinish()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
