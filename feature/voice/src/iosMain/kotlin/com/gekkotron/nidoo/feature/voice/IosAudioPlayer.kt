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

    override suspend fun play(wav: ByteArray): Boolean {
        if (wav.isEmpty()) return false
        val player = orNilInit { AVAudioPlayer(data = wav.toNSData(), error = null) } ?: return false
        if (!player.prepareToPlay()) return false
        var playing = false
        try {
            withTimeoutOrNull((player.duration * 1000).toLong() + END_GRACE_MS) {
                suspendCancellableCoroutine { cont ->
                    val resume = { if (cont.isActive) cont.resume(Unit) }
                    finished = resume
                    delegate = FinishDelegate(resume).also { player.delegate = it }
                    playing = player.play()
                    if (!playing) resume()
                }
            }
        } finally {
            player.delegate = null
            player.stop()
            finished = null
            delegate = null
        }
        return playing
    }

    override fun stop() {
        finished?.invoke()
    }
}

private class FinishDelegate(private val onFinish: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onFinish()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
