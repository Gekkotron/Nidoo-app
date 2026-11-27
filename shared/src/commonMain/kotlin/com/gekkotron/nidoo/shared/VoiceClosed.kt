package com.gekkotron.nidoo.shared

/** The voice overlay was closed: the platform releases whatever listening held (iOS gives the audio session back). */
expect fun onVoiceClosed()
