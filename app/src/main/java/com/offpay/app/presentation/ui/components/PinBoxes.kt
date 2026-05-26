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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors

/**
 * Reusable PIN-display row.
 *
 * Renders [length] sharp 52dp boxes (default 6) reflecting the typed [value]:
 *  - Empty box: 2dp [NeoPopColors.BorderStrong] border on a SurfaceHigher
 *    fill (lighter than pure black) so the boxes read clearly against the
 *    PIN section's accent card on dark backgrounds.
 *  - Filled box: lime accent fill at 18% alpha + 2dp solid lime border,
 *    with a 10dp white dot dead-centre.
 *  - Active box (the next one to be filled): solid lime border + a softer
 *    pulsing lime inner-glow ring so the user knows where the next digit
 *    will land. The pulse lives on a separate inset ring rather than the
 *    box's overall opacity, so the box itself always stays visible.
 *
 * Polish pass: the previous version pulsed the entire active box's alpha,
 * which on dark backgrounds made it look like the box disappeared briefly
 * each cycle — users reported "the PIN window isn't visible". Replaced
 * with a fixed-opacity box + a separate animated inner ring that fades
 * in and out without affecting the box's structure.
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
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pin_pulse_alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
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
                filled -> NeoPopColors.Accent.copy(alpha = 0.18f)
                isActive -> NeoPopColors.SurfaceHigher
                else -> NeoPopColors.SurfaceHigher
            }

            Box(
                Modifier
                    .size(52.dp)
                    .background(fillColor)
                    .drawBehind {
                        // Outer fixed border — always visible.
                        drawRect(
                            color = borderColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Active-state pulse: an inset ring inside the
                        // box, alpha-pulsed, so the cursor reads without
                        // ever making the whole box appear to vanish.
                        if (isActive) {
                            val inset = 4.dp.toPx()
                            drawRect(
                                color = NeoPopColors.Accent.copy(alpha = pulseAlpha),
                                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width - inset * 2f,
                                    size.height - inset * 2f
                                ),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (filled) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(NeoPopColors.TextPrimary)
                    )
                }
            }
        }
    }
}
