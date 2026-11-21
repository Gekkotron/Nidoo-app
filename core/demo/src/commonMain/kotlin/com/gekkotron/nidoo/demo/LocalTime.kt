package com.gekkotron.nidoo.demo

/** `HH:mm` of [epochSeconds] in the phone's time zone, for the demo boost's "until" text. */
internal expect fun localHourMinute(epochSeconds: Long): String
