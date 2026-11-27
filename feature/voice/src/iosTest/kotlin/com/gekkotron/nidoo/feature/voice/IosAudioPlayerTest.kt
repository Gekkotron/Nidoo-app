package com.gekkotron.nidoo.feature.voice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class IosAudioPlayerTest {
    @Test
    fun garbageWavIsNotPlayable() = runTest {
        assertFalse(IosAudioPlayer().play(byteArrayOf(1, 2, 3)))
    }
}
