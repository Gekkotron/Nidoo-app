package com.gekkotron.nidoo.crypto

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Envelopes captured from the JVM (javax.crypto) implementation before the module went multiplatform
 * (iOS spec §8): every platform must seal and open them byte for byte. Key 00..1f, nonce a0..ab.
 */
class EnvelopeVectorsTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (0xa0 + it).toByte() }
    private val jsonPlaintext = """{"timestamp":1700000000,"cmd":"on"}"""
    private val jsonVector = "oKGio6SlpqeoqaqrnToIRCiuccsDCPfxPUv37kCcaSCih3JAvm1L4l2RV268VDrYsY/9llskycteXVdkzC1f"
    private val emptyVector = "oKGio6SlpqeoqaqrXGmWJa9Lk6D4IgoqYRnF0A=="

    private fun crypto() = EnvelopeCrypto(key, nonceSource = { nonce.copyOf() })

    @Test
    fun sealMatchesJvmVector() {
        assertEquals(jsonVector, crypto().sealBytes(jsonPlaintext.encodeToByteArray()))
        assertEquals(emptyVector, crypto().sealBytes(ByteArray(0)))
    }

    @Test
    fun opensJvmVector() {
        val obj = crypto().open(jsonVector)
        assertEquals(1700000000L, obj["timestamp"]!!.jsonPrimitive.long)
        assertEquals("on", obj["cmd"]!!.jsonPrimitive.content)
    }

    @Test
    fun opensUnpaddedEnvelope() {
        // java.util.Base64's decoder accepts a missing `=`; the multiplatform decoder must too (Review Focus #1).
        // The empty plaintext is not JSON, so getting as far as Malformed("plaintext is not JSON") proves decryption.
        val unpadded = emptyVector.trimEnd('=')
        val error = assertFailsWith<EnvelopeException.Malformed> { crypto().open(unpadded) }
        assertEquals("malformed envelope: plaintext is not JSON", error.message)
    }

    @Test
    fun tamperedTagFailsAuthentication() {
        val raw = Base64.decode(jsonVector)
        raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
        assertFailsWith<EnvelopeException.AuthenticationFailed> { crypto().open(Base64.encode(raw)) }
    }

    @Test
    fun nonceIsTwelveRandomBytes() {
        val c = EnvelopeCrypto(key)
        val plaintext = jsonPlaintext.encodeToByteArray()
        val first = Base64.decode(c.sealBytes(plaintext))
        val second = Base64.decode(c.sealBytes(plaintext))
        assertEquals(12 + plaintext.size + 16, first.size)
        assertFalse(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
        assertTrue(first.copyOfRange(0, 12).any { it != 0.toByte() })
    }
}
