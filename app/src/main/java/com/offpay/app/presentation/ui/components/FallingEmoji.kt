package com.offpay.app.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Single emoji that falls from y=-64dp (off the top of the screen) to
 * [fallToPx] over [durationMs], starting after [delayMs]. Used by the
 * Settings cat-rain easter egg and the Pay-screen money-drop easter egg.
 *
 * Renders absolutely-positioned via [Modifier.offset] (we drive layout
 * from animated x/y rather than wrapping the parent) so callers can layer
 * many of these on top of any composable tree without disturbing flow.
 */
@Composable
fun FallingEmoji(
    emoji: String,
    xPx: Float,
    fallToPx: Float,
    durationMs: Int,
    delayMs: Long,
    sizeSp: Int
) {
    val density = LocalDensity.current
    var started by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = started,
        animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        label = "fall"
    )

    LaunchedEffect(Unit) {
        delay(delayMs)
        started = 1f
    }

    val startY = -64f
    val y = startY + (fallToPx - startY) * progress
    val rotation = progress * 720f

    Text(
        text = emoji,
        fontSize = sizeSp.sp,
        modifier = Modifier
            .offset { IntOffset(xPx.toInt(), y.toInt()) }
            .graphicsLayer { rotationZ = rotation }
    )
}
