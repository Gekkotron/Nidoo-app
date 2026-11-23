package com.gekkotron.nidoo.shared

import platform.Foundation.NSLog

/**
 * iOS's `Log.w`: callers pass only the repositories' own warning text (parse warnings), never a URL with a key or a payload.
 * [message] goes through `%@` so a `%` in it is never read as a format.
 */
internal fun iosLog(tag: String, message: String) {
    NSLog("%@: %@", tag, message)
}
