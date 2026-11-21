package com.gekkotron.nidoo.data

import com.gekkotron.nidoo.crypto.AES_GCM_KEY_BYTES
import com.gekkotron.nidoo.crypto.AES_GCM_NONCE_BYTES
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeychainSecretStoreTest {
    private class FakeKeychain : Keychain {
        val items = mutableMapOf<Pair<String, String>, ByteArray>()
        var readStatus: Int? = null // a forced failure status for every read
        var addStatus: Int? = null // a forced status for every add (the item is not stored)
        var adds = 0

        override fun read(service: String, account: String): KeychainRead {
            readStatus?.let { return KeychainRead.Failed(it) }
            return items[service to account]?.let { KeychainRead.Found(it.copyOf()) } ?: KeychainRead.NotFound
        }

        override fun add(service: String, account: String, value: ByteArray): Int {
            adds++
            addStatus?.let { return it }
            if ((service to account) in items) return ERR_DUPLICATE
            items[service to account] = value.copyOf()
            return 0
        }
    }

    private val plain = "p@ss wörd 🔑".encodeToByteArray()

    @Test
    fun roundTrip() {
        val store = KeychainSecretStore(keychain = FakeKeychain())
        val sealed = store.encrypt(plain)
        assertEquals(AES_GCM_NONCE_BYTES + plain.size + 16, sealed.size)
        assertContentEquals(plain, store.decrypt(sealed))
    }

    @Test
    fun keyIsCreatedOnceAndReused() {
        val keychain = FakeKeychain()
        val sealed = KeychainSecretStore(keychain = keychain).encrypt(plain)
        assertContentEquals(plain, KeychainSecretStore(keychain = keychain).decrypt(sealed))
        assertEquals(1, keychain.adds)
        assertEquals(AES_GCM_KEY_BYTES, keychain.items.values.single().size)
    }

    @Test
    fun tamperedCipherIsUnavailable() {
        val store = KeychainSecretStore(keychain = FakeKeychain())
        val sealed = store.encrypt(plain)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        assertFailsWith<SecretUnavailable> { store.decrypt(sealed) }
        assertFailsWith<SecretUnavailable> { store.decrypt(ByteArray(5)) }
    }

    @Test
    fun lockedKeychainIsUnavailable() {
        val keychain = FakeKeychain().apply { readStatus = ERR_INTERACTION_NOT_ALLOWED }
        val store = KeychainSecretStore(keychain = keychain)
        assertFailsWith<SecretUnavailable> { store.encrypt(plain) }
        assertFailsWith<SecretUnavailable> { store.decrypt(ByteArray(40)) }
        assertEquals(0, keychain.adds)
    }

    @Test
    fun existingKeyIsNeverReplaced() {
        val keychain = FakeKeychain()
        val sealed = KeychainSecretStore(keychain = keychain).encrypt(plain)
        val storedKey = keychain.items.values.single().copyOf()
        // Another instance misses the key on its first read (a race), then add reports a duplicate.
        val racing = object : Keychain {
            var first = true
            override fun read(service: String, account: String): KeychainRead =
                if (first) KeychainRead.NotFound.also { first = false } else keychain.read(service, account)
            override fun add(service: String, account: String, value: ByteArray): Int = keychain.add(service, account, value)
        }
        val second = KeychainSecretStore(keychain = racing)
        assertContentEquals(plain, second.decrypt(second.encrypt(plain)))
        assertContentEquals(plain, second.decrypt(sealed))
        assertContentEquals(storedKey, keychain.items.values.single())
    }

    @Test
    fun aMissingKeyIsNotCreatedToDecrypt() {
        val keychain = FakeKeychain()
        assertFailsWith<SecretUnavailable> { KeychainSecretStore(keychain = keychain).decrypt(ByteArray(40)) }
        assertEquals(0, keychain.adds)
    }

    private companion object {
        const val ERR_DUPLICATE = -25299
        const val ERR_INTERACTION_NOT_ALLOWED = -25308
    }
}
