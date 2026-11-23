package com.gekkotron.nidoo.format

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun nfd(text: String): String = (text as NSString).decomposedStringWithCanonicalMapping
