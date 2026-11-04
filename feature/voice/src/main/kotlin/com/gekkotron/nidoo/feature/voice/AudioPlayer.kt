package com.gekkotron.nidoo.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.gekkotron.nidoo.assist.WavParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Plays the hub's WAV. [play] suspends until the audio ends; false means it was not playable. */
interface AudioPlayer {
    suspend fun play(wav: ByteArray): Boolean

    fun stop()
}

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private var track: AudioTrack? = null
    private var finished: (() -> Unit)? = null

    override suspend fun play(wav: ByteArray): Boolean {
        val parsed = WavParser.parse(wav) ?: return false
        val channelMask = when (parsed.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return false
        }
        val frameBytes = 2 * parsed.channels
        val frames = parsed.pcm.size / frameBytes
        if (parsed.bitsPerSample != 16 || frames == 0) return false
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(parsed.sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(frames * frameBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: IllegalArgumentException) {
            return false
        } catch (e: UnsupportedOperationException) {
            return false
        }
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            return false
        }
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        try {
            audioTrack.write(parsed.pcm, 0, frames * frameBytes)
            audioManager.requestAudioFocus(focus)
            suspendCancellableCoroutine { cont ->
                synchronized(lock) {
                    track = audioTrack
                    finished = { if (cont.isActive) cont.resume(Unit) }
                }
                audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) { if (cont.isActive) cont.resume(Unit) }
                    override fun onPeriodicNotification(t: AudioTrack?) = Unit
                })
                audioTrack.setNotificationMarkerPosition(frames)
                audioTrack.play()
            }
            return true
        } finally {
            synchronized(lock) {
                track = null
                finished = null
            }
            audioTrack.stop()
            audioTrack.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    override fun stop() {
        val resume = synchronized(lock) { finished }
        resume?.invoke()
    }
}
