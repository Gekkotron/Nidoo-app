package com.gekkotron.nidoo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

sealed interface PayloadError {
    data object NotNidooCode : PayloadError
    data class UnsupportedVersion(val version: Int) : PayloadError
    data class InvalidField(val field: String) : PayloadError
}

sealed interface ParseResult {
    data class Ok(val profile: ConnectionProfile.Direct) : ParseResult
    data class Error(val error: PayloadError) : ParseResult
}

/** The setup QR code, format `v:1` (parent spec §5.2, this spec §4.4), with optional `gateway`, `push`, `history` and `frigate` sections. */
object OnboardingPayload {
    fun parse(text: String): ParseResult {
        val root = try {
            Json.parseToJsonElement(text.trim()) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return error(PayloadError.NotNidooCode)
        val version = root.int("v") ?: return error(PayloadError.NotNidooCode)
        if (version != 1) return error(PayloadError.UnsupportedVersion(version))

        val local = root["local"] as? JsonObject ?: return invalid("local")
        val host = local.string("host")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("local.host")
        val port = if ("port" in local) local.int("port")?.takeIf { it in 1..65535 } ?: return invalid("local.port") else 1883
        val broker = LocalBroker(
            host = host,
            port = port,
            tls = local.flag("tls") ?: return invalid("local.tls"),
            ws = local.flag("ws") ?: return invalid("local.ws"),
            username = local.string("user")?.takeIf { it.isNotEmpty() },
            password = local.string("pass")?.takeIf { it.isNotEmpty() },
        )

        val gateway = (root["gateway"] as? JsonObject)?.let { g ->
            val url = g.string("url")?.takeIf(::isGatewayUrl) ?: return invalid("gateway.url")
            val key = g.string("key")?.let(::hexToBytes)?.takeIf { it.size == GatewayConfig.KEY_BYTES }
                ?: return invalid("gateway.key")
            GatewayConfig(url, key, g.string("broker_host")?.takeIf { it.isNotEmpty() })
        }
        val prefix = if ("prefix" in root) root.string("prefix")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("prefix") else DEFAULT_PREFIX
        val tokenTopic = (root["push"] as? JsonObject)?.string("token_topic")?.takeIf { it.isNotEmpty() }
        val history = (root["history"] as? JsonObject)?.let { h ->
            h.string("url")?.let(::normalizeHistoryUrl) ?: return invalid("history.url")
        }
        val frigate = (root["frigate"] as? JsonObject)?.let { f ->
            val url = f.string("url")?.let(::normalizeFrigateUrl) ?: return invalid("frigate.url")
            val user = f.string("user")?.takeIf { it.isNotEmpty() }
            val pass = f.string("pass")?.takeIf { it.isNotEmpty() }
            if (pass != null && user == null) return invalid("frigate.user")
            FrigateConfig(url, user, pass)
        }
        return ParseResult.Ok(ConnectionProfile.Direct(broker, prefix, gateway, tokenTopic, history, frigate))
    }

    private fun error(e: PayloadError) = ParseResult.Error(e)
    private fun invalid(field: String) = ParseResult.Error(PayloadError.InvalidField(field))

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    /** Absent means false; present but not a JSON boolean means invalid (null). */
    private fun JsonObject.flag(key: String): Boolean? =
        if (key !in this) false else (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    /**
     * An https URL with a host and, if given, a port in 1..65535. The gateway SDK throws on anything less,
     * and a saved profile with such a URL must never reach it. Parsed here in common code: scheme, then the
     * authority up to the first `/`, `?` or `#`, without any `user@`, split into host and port.
     */
    private fun isGatewayUrl(url: String): Boolean {
        if (!url.startsWith(HTTPS) || url.any { it.isWhitespace() || it.isISOControl() || it in URL_FORBIDDEN }) return false
        val rest = url.substring(HTTPS.length)
        val authority = rest.substring(0, rest.indexOfAny(charArrayOf('/', '?', '#')).takeIf { it >= 0 } ?: rest.length)
            .substringAfterLast('@')
        val host: String
        val port: String?
        if (authority.startsWith('[')) {
            val close = authority.indexOf(']').takeIf { it > 1 } ?: return false
            host = authority.substring(1, close)
            val after = authority.substring(close + 1)
            if (after.isNotEmpty() && !after.startsWith(':')) return false
            port = after.takeIf { it.isNotEmpty() }?.substring(1)
            if (!host.all { it == ':' || it == '.' || it.digitToIntOrNull(16) != null } || ':' !in host) return false
        } else {
            host = authority.substringBeforeLast(':')
            port = if (host == authority) null else authority.substringAfterLast(':')
            if (!isLooseHost(host)) return false
        }
        return port == null || port.all { it in '0'..'9' } && port.toIntOrNull() in 1..65535
    }

    /** Dot-separated labels of letters (any script), digits, `-` and `_`, with an optional final dot. */
    private fun isLooseHost(host: String): Boolean {
        val name = host.removeSuffix(".")
        return name.isNotEmpty() && name.split('.').all { label ->
            label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }

    private const val HTTPS = "https://"

    /** Characters java.net.URI refused anywhere in a URL. */
    private const val URL_FORBIDDEN = "\"<>\\^`{|}"

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(hex.length / 2) { i -> ((hex[2 * i].digitToInt(16) shl 4) or hex[2 * i + 1].digitToInt(16)).toByte() }
    }
}
