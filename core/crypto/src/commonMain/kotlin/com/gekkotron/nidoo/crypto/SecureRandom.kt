package com.gekkotron.nidoo.crypto

/** [size] bytes from the platform's cryptographically secure generator. */
internal expect fun secureRandomBytes(size: Int): ByteArray
