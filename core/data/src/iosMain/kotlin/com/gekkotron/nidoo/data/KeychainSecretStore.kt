package com.gekkotron.nidoo.data

import com.gekkotron.nidoo.crypto.AES_GCM_KEY_BYTES
import com.gekkotron.nidoo.crypto.AesGcm256
import com.gekkotron.nidoo.crypto.newKey
import com.gekkotron.nidoo.crypto.openNonceFirst
import com.gekkotron.nidoo.crypto.sealWithRandomNonce
import kotlin.concurrent.Volatile

/**
 * AES-256-GCM with a random key kept in the Keychain (the iOS counterpart of [KeystoreSecretStore] on Android).
 * Output: `nonce[12] ‖ ciphertext ‖ tag[16]`. Every failure is [SecretUnavailable], so a locked phone shows
 * the profile as Locked instead of crashing. A key in the Keychain is never replaced: losing it would make
 * every stored secret unreadable.
 */
class KeychainSecretStore internal constructor(
    private val service: String,
    private val account: String,
    private val keychain: Keychain,
) : SecretStore {
    constructor(service: String = DEFAULT_SERVICE, account: String = DEFAULT_ACCOUNT) : this(service, account, SystemKeychain)

    internal constructor(keychain: Keychain) : this(DEFAULT_SERVICE, DEFAULT_ACCOUNT, keychain)

    @Volatile
    private var key: ByteArray? = null

    override fun encrypt(plain: ByteArray): ByteArray {
        val k = key ?: storedKey() ?: createKey()
        return guard("cannot encrypt") { AesGcm256.sealWithRandomNonce(k, plain) }
    }

    override fun decrypt(cipher: ByteArray): ByteArray {
        // Never create a key to decrypt: a missing key means the data is unrecoverable.
        val k = key ?: storedKey() ?: throw SecretUnavailable("keychain key missing")
        return guard("cannot decrypt") { AesGcm256.openNonceFirst(k, cipher) }
    }

    /** The stored key, null when there is none; any other Keychain answer (locked, denied) throws. */
    private fun storedKey(): ByteArray? = when (val read = keychain.read(service, account)) {
        is KeychainRead.Found -> read.value.also {
            if (it.size != AES_GCM_KEY_BYTES) throw SecretUnavailable("keychain key has ${it.size} bytes")
            key = it
        }
        KeychainRead.NotFound -> null
        is KeychainRead.Failed -> throw SecretUnavailable("keychain read failed: OSStatus ${read.status}")
    }

    private fun createKey(): ByteArray {
        val fresh = AesGcm256.newKey()
        return when (val status = keychain.add(service, account, fresh)) {
            ERR_SUCCESS -> fresh.also { key = it }
            // Another instance or thread added one first: use that one, never overwrite it.
            ERR_DUPLICATE_ITEM -> storedKey() ?: throw SecretUnavailable("keychain key vanished after a duplicate")
            else -> throw SecretUnavailable("keychain add failed: OSStatus $status")
        }
    }

    private inline fun <T> guard(message: String, block: () -> T): T = try {
        block()
    } catch (e: SecretUnavailable) {
        throw e
    } catch (e: Exception) {
        throw SecretUnavailable(message, e)
    }

    private companion object {
        const val DEFAULT_SERVICE = "com.gekkotron.nidoo.secrets"
        const val DEFAULT_ACCOUNT = "nidoo.secrets.v1"
        const val ERR_SUCCESS = 0
        const val ERR_DUPLICATE_ITEM = -25299
    }
}
