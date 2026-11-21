package com.gekkotron.nidoo.transport

import kotlinx.io.IOException

/** A raw GET (gateway v1.3) or a JSON POST (gateway `http`, v1.4 for headers) through the gateway (push alerts spec §6.3, Frigate auth spec §4). */
interface GatewayDownloader {
    /** @throws DownloadNotAllowed the gateway key has no rule for this destination or port. @throws DownloadFailed anything else. */
    suspend fun download(url: String, timeoutSeconds: Int, headers: Map<String, String> = emptyMap()): RawDownload

    /** Same failures as [download]. A gateway older than v1.4 answers with no headers. */
    suspend fun post(url: String, json: String, timeoutSeconds: Int): RawPost
}

/** [status] may be an upstream error (404...): the caller decides what that means. */
class RawDownload(val status: Int, val bytes: ByteArray, val contentType: String?)

/** [headers]: lower-case names, repeated headers joined with `\n`. [body]: the response text, empty when there is none. */
class RawPost(val status: Int, val headers: Map<String, String>, val body: String = "")

/** "destination not allowed for this secret": the user has to add the host to the key's rules (spec §10 step 2). */
class DownloadNotAllowed(val reason: String) : Exception(reason)

class DownloadFailed(message: String, cause: Throwable? = null) : IOException(message, cause)
