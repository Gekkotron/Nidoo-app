package com.gekkotron.nidoo.push

import com.gekkotron.nidoo.alerts.Alert
import com.gekkotron.nidoo.alerts.AlertAction
import com.gekkotron.nidoo.alerts.AlertChannel
import com.gekkotron.nidoo.alerts.AlertLoc
import com.gekkotron.nidoo.alerts.Severity
import java.time.Instant

fun testAlert(
    id: String = "frigate-1",
    title: String = "Front door",
    body: String = "Person detected",
    channel: AlertChannel = AlertChannel.CAMERA,
    imageUrl: String? = null,
    actions: List<AlertAction> = emptyList(),
    silent: Boolean = false,
    resolved: Boolean = false,
    sent: Instant = Instant.parse("2026-10-07T10:00:00Z"),
    loc: AlertLoc? = null,
) = Alert(
    id = id, channel = channel, severity = Severity.WARNING, title = title, body = body, imageUrl = imageUrl, videoUrl = null,
    entityId = null, actions = actions, source = "frigate", silent = silent, resolved = resolved, sentAt = sent, receivedAt = sent, loc = loc,
)
