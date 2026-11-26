package com.gekkotron.nidoo.feature.security.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.gekkotron.nidoo.feature.security.resources.*
import com.gekkotron.nidoo.feature.security.presentation.SinceSpan
import kotlin.time.Instant

/** "2 h", "<1 min", "30+ d": the tile's compact form. */
@Composable
internal fun sinceShort(span: SinceSpan): String = when (span) {
    SinceSpan.UnderAMinute -> stringResource(Res.string.security_since_short_now)
    is SinceSpan.Minutes -> stringResource(Res.string.security_since_short_minutes, span.n.toInt())
    is SinceSpan.Hours -> stringResource(Res.string.security_since_short_hours, span.n.toInt())
    is SinceSpan.Days -> stringResource(Res.string.security_since_short_days, span.n.toInt())
    is SinceSpan.Over -> stringResource(Res.string.security_since_short_over, span.days.toInt())
}

/** "2 hours", "more than 30 days": what TalkBack reads. */
@Composable
internal fun sinceLong(span: SinceSpan): String = when (span) {
    SinceSpan.UnderAMinute -> stringResource(Res.string.security_since_long_now)
    is SinceSpan.Minutes -> pluralStringResource(Res.plurals.security_since_long_minutes, span.n.toInt(), span.n.toInt())
    is SinceSpan.Hours -> pluralStringResource(Res.plurals.security_since_long_hours, span.n.toInt(), span.n.toInt())
    is SinceSpan.Days -> pluralStringResource(Res.plurals.security_since_long_days, span.n.toInt(), span.n.toInt())
    is SinceSpan.Over -> pluralStringResource(Res.plurals.security_since_long_over, span.days.toInt(), span.days.toInt())
}

/** "08:42" today, "Tue 08:42" within six days, "3 Oct, 08:42" before: the platform's own date patterns (last-change spec §4.5). */
@Composable
internal expect fun sinceDateText(at: Instant, now: Instant): String
