package com.gekkotron.nidoo.history

import java.time.Instant
import java.time.ZoneId

internal actual fun parisHourOfDay(epochSeconds: Long): Double {
    val z = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of("Europe/Paris"))
    return z.hour + z.minute / 60.0
}
