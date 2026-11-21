package com.gekkotron.nidoo.transport

/** How a LAN URL can be reached right now: a local probe when a LAN broker is allowed, a gateway when allowed. */
class HttpRoute(val local: LocalProbe?, val gateway: GatewayDownloader?)

fun interface HttpRoutes {
    suspend fun current(): HttpRoute
}

sealed interface HttpGetResult {
    /** Any HTTP answer; a non-2xx LAN answer has an empty body (it is never read). */
    class Response(val status: Int, val bytes: ByteArray, val contentType: String?) : HttpGetResult
    data object TooLarge : HttpGetResult
    /** The gateway key has no rule for this host or port. */
    data object NotAllowed : HttpGetResult
    data class Unavailable(val reason: String) : HttpGetResult
}

sealed interface HttpPostResult {
    /** Any HTTP answer, with its headers (lower-case names, repeated ones joined with `\n`) and its body text (empty when over `LanOrGatewayGet`'s post body cap). */
    class Response(val status: Int, val headers: Map<String, String>, val body: String = "") : HttpPostResult
    data object NotAllowed : HttpPostResult
    data class Unavailable(val reason: String) : HttpPostResult
}
