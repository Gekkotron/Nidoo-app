package com.gekkotron.nidoo.crypto

import java.security.SecureRandom

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
