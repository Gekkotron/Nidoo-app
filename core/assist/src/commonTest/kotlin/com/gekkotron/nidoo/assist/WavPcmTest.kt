package com.gekkotron.nidoo.assist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WavPcmTest {
    private fun le32(v: Long) = ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() }
    private fun le16(v: Int) = ByteArray(2) { ((v shr (8 * it)) and 0xFF).toByte() }

    private fun fmt(tag: Int = 1) =
        "fmt ".encodeToByteArray() + le32(16) + le16(tag) + le16(1) + le32(24000) + le32(48000) + le16(2) + le16(16)

    private fun wav(
        pcmBytes: Int,
        dataSize: Long = pcmBytes.toLong(),
        riff: String = "RIFF",
        fmtChunk: ByteArray? = fmt(),
        extra: ByteArray = ByteArray(0),
    ): ByteArray =
        riff.encodeToByteArray() + le32(0xFFFFFFFFL) + "WAVE".encodeToByteArray() +
            (fmtChunk ?: ByteArray(0)) + extra +
            "data".encodeToByteArray() + le32(dataSize) + ByteArray(pcmBytes) { (it % 100).toByte() }

    @Test
    fun `parses a 24 kHz mono 16-bit file`() {
        val wav = assertNotNull(WavParser.parse(wav(480)))
        assertEquals(24000, wav.sampleRate)
        assertEquals(1, wav.channels)
        assertEquals(16, wav.bitsPerSample)
        assertEquals(480, wav.pcm.size)
    }

    @Test
    fun `streamed header length is ignored`() {
        assertEquals(480, WavParser.parse(wav(480, dataSize = 0xFFFFFFFFL))?.pcm?.size)
    }

    @Test
    fun `odd trailing byte is dropped`() {
        assertEquals(480, WavParser.parse(wav(481, dataSize = 0xFFFFFFFFL))?.pcm?.size)
    }

    @Test
    fun `skips an unknown chunk before data`() {
        val list = "LIST".encodeToByteArray() + le32(5) + ByteArray(5) + ByteArray(1)
        assertEquals(480, WavParser.parse(wav(480, extra = list))?.pcm?.size)
    }

    @Test
    fun `not RIFF is null`() {
        assertNull(WavParser.parse(wav(480, riff = "RIFX")))
    }

    @Test
    fun `missing fmt is null`() {
        assertNull(WavParser.parse(wav(480, fmtChunk = null)))
    }

    @Test
    fun `non-PCM format is null`() {
        assertNull(WavParser.parse(wav(480, fmtChunk = fmt(tag = 3))))
    }
}
