package com.offpay.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors

/**
 * Reusable PIN-display row.
 *
 * Renders [length] sharp 48dp boxes (default 6) reflecting the typed [value]:
 *  - Empty box: 1.5dp [NeoPopColors.BorderStrong] border on a black square.
 *  - Filled box: lime accent fill at 14% alpha + 1.5dp lime border, with a
 *    small 8dp white dot dead-center.
 *  - Active box (the next one to be filled): swaps the grey border for a
 *    lime one and pulses its opacity on a 0.5s cycle so the cursor reads
 *    even though the backing field is hidden.
 *
 * No corner rounding — flat 90° NeoPOP corners. The component is purely
 * visual; input is owned by the caller (typically a hidden BasicTextField)
 * and the [value] is passed in.
 */
@Composable
fun PinBoxes(
    value: String,
    modifier: Modifier = Modifier,
    length: Int = 6
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pin_active")
    val activeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pin_active_alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(length) { index ->
            val filled = index < value.length
            val isActive = index == value.length
            val borderColor by animateColorAsState(
                targetValue = when {
                    filled -> NeoPopColors.Accent
                    isActive -> NeoPopColors.Accent
                    else -> NeoPopColors.BorderStrong
                },
                label = "pin_border_$index"
            )
            val fillColor = when {
                filled -> NeoPopColors.Accent.copy(alpha = 0.14f)
                else -> NeoPopColors.Black
            }
            val activeOpacity = if (isActive) activeAlpha else 1f

            Box(
                Modifier
                    .size(48.dp)
                    .alpha(activeOpacity)
                    .background(fillColor)
                    .drawBehind {
                        drawRect(
                            color = borderColor,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (filled) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(NeoPopColors.TextPrimary)
                    )
                }
            }
        }
    }
}
