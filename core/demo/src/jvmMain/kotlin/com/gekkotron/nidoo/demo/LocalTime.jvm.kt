package com.gekkotron.nidoo.demo

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun localHourMinute(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
