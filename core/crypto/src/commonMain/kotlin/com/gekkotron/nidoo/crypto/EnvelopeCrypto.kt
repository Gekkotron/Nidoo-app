package com.gekkotron.nidoo.crypto

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.time.Clock

/**
 * AES-256-GCM envelope used by Athena-HttpMqttGateway:
 * `base64(nonce[12] ‖ ciphertext ‖ tag[16])` over UTF-8 JSON that carries `timestamp` (epoch seconds).
 */
class EnvelopeCrypto(
    key: ByteArray,
    private val maxAgeSeconds: Long = 60,
    private val nowSeconds: () -> Long = { Clock.System.now().epochSeconds },
    private val nonceSource: () -> ByteArray = ::randomNonce,
) {
    private val key: ByteArray

    init {
        require(key.size == KEY_BYTES) { "AES-256 key must be $KEY_BYTES bytes" }
        this.key = key.copyOf()
    }

    /** Encrypts [fields], adding `timestamp` when absent. */
    fun seal(fields: JsonObject): String {
        val stamped = if ("timestamp" in fields) fields else JsonObject(fields + ("timestamp" to JsonPrimitive(nowSeconds())))
        return sealBytes(stamped.toString().encodeToByteArray())
    }

    fun sealBytes(plaintext: ByteArray): String {
        val nonce = nonceSource()
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        return Base64.encode(nonce + AesGcm256.seal(key, nonce, plaintext))
    }

    /** Decrypts without a freshness check (used for SSE events). */
    fun open(envelope: String): JsonObject {
        val raw = try {
            LENIENT_BASE64.decode(envelope.trim())
        } catch (e: IllegalArgumentException) {
            throw EnvelopeException.Malformed("not base64")
        }
        if (raw.size < NONCE_BYTES + TAG_BITS / 8) throw EnvelopeException.Malformed("too short")
        val plaintext = AesGcm256.open(key, raw.copyOfRange(0, NONCE_BYTES), raw.copyOfRange(NONCE_BYTES, raw.size))
        val element = try {
            Json.parseToJsonElement(plaintext.decodeToString())
        } catch (e: SerializationException) {
            throw EnvelopeException.Malformed("plaintext is not JSON")
        }
        return element as? JsonObject ?: throw EnvelopeException.Malformed("plaintext is not a JSON object")
    }

    /** Decrypts and rejects a `timestamp` outside ±maxAgeSeconds (used for responses). */
    fun openFresh(envelope: String): JsonObject {
        val obj = open(envelope)
        val ts = (obj["timestamp"] as? JsonPrimitive)?.doubleOrNull?.toLong()
            ?: throw EnvelopeException.MissingTimestamp()
        val skew = nowSeconds() - ts
        if (abs(skew) > maxAgeSeconds) throw EnvelopeException.Expired(skew)
        return obj
    }

    companion object {
        const val KEY_BYTES = AES_GCM_KEY_BYTES
        const val NONCE_BYTES = AES_GCM_NONCE_BYTES
        private const val TAG_BITS = AES_GCM_TAG_BYTES * 8

        /** java.util.Base64's decoder took input with or without `=` padding; so does this one. */
        private val LENIENT_BASE64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        /** Parses the 64-character hex key from the onboarding QR payload. */
        fun keyFromHex(hex: String): ByteArray {
            val clean = hex.trim()
            require(clean.length == KEY_BYTES * 2 && clean.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                "key must be ${KEY_BYTES * 2} hex characters"
            }
            return ByteArray(KEY_BYTES) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
    }
}

private fun randomNonce(): ByteArray = secureRandomBytes(EnvelopeCrypto.NONCE_BYTES)
