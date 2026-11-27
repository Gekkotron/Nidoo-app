package com.gekkotron.nidoo.shared

/** Android's recognizer and player release their own audio focus. */
actual fun onVoiceClosed() = Unit
