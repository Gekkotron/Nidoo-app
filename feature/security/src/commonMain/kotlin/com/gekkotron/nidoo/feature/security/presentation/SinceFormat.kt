package com.gekkotron.nidoo.feature.security.presentation

import kotlin.time.Duration
import kotlin.time.Instant

/** How long a state has lasted, rounded down to one unit (last-change spec §4.5). */
sealed interface SinceSpan {
    data object UnderAMinute : SinceSpan
    data class Minutes(val n: Long) : SinceSpan
    data class Hours(val n: Long) : SinceSpan
    data class Days(val n: Long) : SinceSpan
    data class Over(val days: Long) : SinceSpan
}

object SinceFormat {
    fun span(since: Since, now: Instant): SinceSpan = when (since) {
        is Since.Over -> SinceSpan.Over(since.window.inWholeDays)
        is Since.At -> {
            // A server clock ahead of the phone gives a negative span: read it as "just now".
            val s = (now - since.at).inWholeSeconds
            when {
                s < 60 -> SinceSpan.UnderAMinute
                s < 3_600 -> SinceSpan.Minutes(s / 60)
                s < 48 * 3_600 -> SinceSpan.Hours(s / 3_600)
                else -> SinceSpan.Days(s / 86_400)
            }
        }
    }
}
