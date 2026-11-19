package com.gekkotron.nidoo.shared

import com.gekkotron.nidoo.model.Kind

/** One line proving the iOS app runs the shared core: how many device kinds it knows. */
fun sharedCoreSummary(): String = "${Kind.entries.size} device kinds from the shared core"
