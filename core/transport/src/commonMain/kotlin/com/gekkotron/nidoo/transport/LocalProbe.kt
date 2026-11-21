package com.gekkotron.nidoo.transport

/** Is the local broker reachable right now? (parent spec §4.5 step 1). */
fun interface LocalProbe {
    suspend fun reachable(): Boolean
}
