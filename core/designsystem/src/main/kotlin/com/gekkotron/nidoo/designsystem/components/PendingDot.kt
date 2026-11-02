package com.gekkotron.nidoo.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gekkotron.nidoo.designsystem.NidooMotion
import com.gekkotron.nidoo.designsystem.NidooTheme

/** Pulses while an optimistic command waits for confirmation. */
@Composable
fun PendingDot(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "pending").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(NidooMotion.MEDIUM, easing = NidooMotion.Emphasized), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier.size(8.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(NidooTheme.colors.solar))
}
