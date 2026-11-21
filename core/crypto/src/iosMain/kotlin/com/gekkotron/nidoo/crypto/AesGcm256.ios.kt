package com.gekkotron.nidoo.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.providers.cryptokit.CryptoKit
import kotlin.coroutines.cancellation.CancellationException

private val gcm by lazy { CryptographyProvider.CryptoKit.get(AES.GCM) }

@OptIn(DelicateCryptographyApi::class)
private fun cipher(key: ByteArray) = gcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher()

@OptIn(DelicateCryptographyApi::class)
internal actual fun platformAesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
    cipher(key).encryptWithIvBlocking(nonce, plaintext)

@OptIn(DelicateCryptographyApi::class)
internal actual fun platformAesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray {
    if (sealed.size < AES_GCM_TAG_BYTES) throw EnvelopeException.AuthenticationFailed()
    return try {
        cipher(key).decryptWithIvBlocking(nonce, sealed)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // CryptoKit reports a bad tag as a Swift error; never surface its text (it may echo inputs).
        throw EnvelopeException.AuthenticationFailed()
    }
}
