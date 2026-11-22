package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.data.ConnectionProfile
import com.gekkotron.nidoo.data.GatewayConfig
import com.gekkotron.nidoo.data.LocalBroker
import com.gekkotron.nidoo.data.ProfileRepository
import com.gekkotron.nidoo.data.SecretStore
import com.gekkotron.nidoo.transport.TransportSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSpecsTest {
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("nidoo-connection-${Random.nextLong().toULong()}")
        .also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(dir)

    /** Reversible, never plaintext: enough for a profile to round-trip. */
    private object XorSecrets : SecretStore {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(cipher: ByteArray) = encrypt(cipher)
    }

    @Test
    fun noProfileHasNoSpec() = runTest {
        val repo = ProfileRepository.create(dir.resolve(ProfileRepository.FILE), XorSecrets, backgroundScope)
        assertNull(profileSpecs(repo).first())
    }

    @Test
    fun demoProfileIsTheDemo() = runTest {
        val repo = ProfileRepository.create(dir.resolve(ProfileRepository.FILE), XorSecrets, backgroundScope)
        repo.save(ConnectionProfile.Demo)
        assertEquals(TransportSpec.Demo, profileSpecs(repo).first { it != null })
    }

    @Test
    fun gatewayProfileIsRouted() = runTest {
        val repo = ProfileRepository.create(dir.resolve(ProfileRepository.FILE), XorSecrets, backgroundScope)
        val gateway = GatewayConfig("https://gw.example", ByteArray(GatewayConfig.KEY_BYTES) { it.toByte() }, null)
        repo.save(ConnectionProfile.Direct(LocalBroker("broker.local", username = "u", password = "p"), gateway = gateway))
        val spec = assertIs<TransportSpec.Routed>(profileSpecs(repo).first { it != null })
        assertTrue(spec.direct.clientId.startsWith("nidoo-"), spec.direct.clientId)
    }
}
