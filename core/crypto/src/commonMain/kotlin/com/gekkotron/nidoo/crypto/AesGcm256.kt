package com.gekkotron.nidoo.crypto

/**
 * AES-256-GCM with a 12-byte nonce and a 16-byte tag, the primitive under [EnvelopeCrypto]. The JVM keeps
 * javax.crypto; iOS goes through CryptoKit (iOS spec §7.2).
 */
object AesGcm256 {
    /** Returns ciphertext‖tag[16]. */
    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray = platformAesGcmSeal(key, nonce, plaintext)

    /** @throws EnvelopeException.AuthenticationFailed on a wrong key, nonce or tampered data. */
    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray = platformAesGcmOpen(key, nonce, sealed)
}

internal expect fun platformAesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray

internal expect fun platformAesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray

const val AES_GCM_KEY_BYTES = 32
const val AES_GCM_NONCE_BYTES = 12
internal const val AES_GCM_TAG_BYTES = 16

/** Seals under a fresh random nonce; returns nonce[12]‖ciphertext‖tag[16]. */
fun AesGcm256.sealWithRandomNonce(key: ByteArray, plaintext: ByteArray): ByteArray {
    val nonce = secureRandomBytes(AES_GCM_NONCE_BYTES)
    return nonce + seal(key, nonce, plaintext)
}

/** Opens what [sealWithRandomNonce] produced. @throws EnvelopeException.AuthenticationFailed */
fun AesGcm256.openNonceFirst(key: ByteArray, data: ByteArray): ByteArray {
    if (data.size < AES_GCM_NONCE_BYTES + AES_GCM_TAG_BYTES) throw EnvelopeException.AuthenticationFailed()
    return open(key, data.copyOfRange(0, AES_GCM_NONCE_BYTES), data.copyOfRange(AES_GCM_NONCE_BYTES, data.size))
}

/** A new random 256-bit key. */
fun AesGcm256.newKey(): ByteArray = secureRandomBytes(AES_GCM_KEY_BYTES)
