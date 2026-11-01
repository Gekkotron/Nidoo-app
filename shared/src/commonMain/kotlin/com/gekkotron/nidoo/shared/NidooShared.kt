package com.gekkotron.nidoo.shared

// Phase 0 scaffold anchor. Proves the Android app links to the KMP shared module at
// build time (via :app dependency) and at runtime (referenced from NidooApplication).
// Remove once another member in commonMain carries that role.
object NidooShared {
    const val VERSION: String = "0.1-kmp-scaffold"
}
