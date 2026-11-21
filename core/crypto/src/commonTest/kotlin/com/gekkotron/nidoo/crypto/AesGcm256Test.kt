package com.gekkotron.nidoo.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesGcm256Test {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (0xa0 + it).toByte() }

    @Test
    fun sealAppendsSixteenByteTagAndOpens() {
        val sealed = AesGcm256.seal(key, nonce, "hello".encodeToByteArray())
        assertEquals(5 + 16, sealed.size)
        assertContentEquals("hello".encodeToByteArray(), AesGcm256.open(key, nonce, sealed))
    }

    @Test
    fun openWithWrongKeyFailsAuthentication() {
        val sealed = AesGcm256.seal(key, nonce, "hello".encodeToByteArray())
        assertFailsWith<EnvelopeException.AuthenticationFailed> { AesGcm256.open(ByteArray(32), nonce, sealed) }
    }

    @Test
    fun nonceFirstRoundTripUsesFreshNonces() {
        val a = AesGcm256.sealWithRandomNonce(key, "x".encodeToByteArray())
        val b = AesGcm256.sealWithRandomNonce(key, "x".encodeToByteArray())
        assertEquals(12 + 1 + 16, a.size)
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
        assertContentEquals("x".encodeToByteArray(), AesGcm256.openNonceFirst(key, a))
    }

    @Test
    fun openNonceFirstRejectsShortInput() {
        assertFailsWith<EnvelopeException.AuthenticationFailed> { AesGcm256.openNonceFirst(key, ByteArray(27)) }
    }

    @Test
    fun newKeyIsThirtyTwoRandomBytes() {
        val a = AesGcm256.newKey()
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(AesGcm256.newKey()))
    }
}
