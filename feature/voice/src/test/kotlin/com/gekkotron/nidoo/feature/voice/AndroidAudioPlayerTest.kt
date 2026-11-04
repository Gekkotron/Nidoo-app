package com.gekkotron.nidoo.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAudioPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a static track waiting for its samples is played, not dropped`() = runTest {
        assertTrue(AndroidAudioPlayer(context).play(wav(frames = 2_400)))
    }

    @Test
    fun `the voice follows the media volume, not the assistant stream some phones keep at zero`() = runTest {
        AndroidAudioPlayer(context).play(wav(frames = 2_400))

        val focus = shadowOf(context.getSystemService(AudioManager::class.java)).lastAudioFocusRequest
        assertEquals(AudioAttributes.USAGE_MEDIA, focus.audioFocusRequest.audioAttributes.usage)
    }

    /** A 24 kHz mono 16-bit WAV with the hub's streamed 0xFFFFFFFF lengths and a non-silent tone. */
    private fun wav(frames: Int): ByteArray {
        val pcm = ByteArray(frames * 2) { i -> if (i % 2 == 1) (if (i / 2 % 24 < 12) 0x20 else -0x20).toByte() else 0 }
        val header = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), -1, -1, -1, -1,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), 16, 0, 0, 0,
            1, 0, 1, 0, 0xC0.toByte(), 0x5D, 0, 0, 0x80.toByte(), 0xBB.toByte(), 0, 0, 2, 0, 16, 0,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), -1, -1, -1, -1,
        )
        return header + pcm
    }
}
