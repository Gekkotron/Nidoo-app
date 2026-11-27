package com.gekkotron.nidoo.feature.voice

/** Plays the hub's WAV. [play] suspends until the audio ends; false means it was not playable. */
interface AudioPlayer {
    suspend fun play(wav: ByteArray): Boolean

    fun stop()
}
