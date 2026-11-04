package com.gekkotron.nidoo.assist

class WavPcm(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val pcm: ByteArray)

/** Parses a RIFF/WAVE file of PCM samples; the `data` chunk is taken to the end of the bytes because the hub streams it with a 0xFFFFFFFF length. */
object WavParser {
    private const val RIFF_HEADER = 12
    private const val CHUNK_HEADER = 8
    private const val FMT_MIN = 16
    private const val FORMAT_PCM = 1

    fun parse(bytes: ByteArray): WavPcm? {
        if (bytes.size < RIFF_HEADER || !tag(bytes, 0, "RIFF") || !tag(bytes, 8, "WAVE")) return null
        var format: IntArray? = null
        var pos = RIFF_HEADER
        while (pos + CHUNK_HEADER <= bytes.size) {
            val size = u32(bytes, pos + 4)
            val body = pos + CHUNK_HEADER
            if (tag(bytes, pos, "fmt ")) {
                if (size < FMT_MIN || body + FMT_MIN > bytes.size) return null
                if (u16(bytes, body) != FORMAT_PCM) return null
                format = intArrayOf(u32(bytes, body + 4).toInt(), u16(bytes, body + 2), u16(bytes, body + 14))
            } else if (tag(bytes, pos, "data")) {
                val f = format ?: return null
                val frame = f[1] * f[2] / 8
                if (frame <= 0) return null
                val available = bytes.size - body
                return WavPcm(f[0], f[1], f[2], bytes.copyOfRange(body, body + available - available % frame))
            }
            val next = body.toLong() + size + (size and 1L)
            if (next > bytes.size) return null
            pos = next.toInt()
        }
        return null
    }

    private fun tag(b: ByteArray, at: Int, s: String) = at + 4 <= b.size && s.indices.all { b[at + it] == s[it].code.toByte() }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)
}
