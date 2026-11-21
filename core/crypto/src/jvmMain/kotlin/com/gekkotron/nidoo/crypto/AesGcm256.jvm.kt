package com.gekkotron.nidoo.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = AES_GCM_TAG_BYTES * 8

internal actual fun platformAesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    return cipher.doFinal(plaintext)
}

internal actual fun platformAesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    return try {
        cipher.doFinal(sealed)
    } catch (e: AEADBadTagException) {
        throw EnvelopeException.AuthenticationFailed()
    }
}
