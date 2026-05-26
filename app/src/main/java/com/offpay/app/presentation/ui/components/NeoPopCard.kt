package com.offpay.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors

/**
 * Static NeoPOP card — same surface technique as [NeoPopButton] but it
 * doesn't move on touch. 4dp depth + 8dp top-face rounding so it reads as
 * decoration, not a control.
 *
 * Polished pass: corners are softened from 0/4dp to 8dp (still geometric)
 * and depth is held at 4dp. Side faces are subtle so the card is calm.
 */
@Composable
fun NeoPopCard(
    modifier: Modifier = Modifier,
    surfaceColor: Color = NeoPopColors.SurfaceHigh,
    rightColor: Color = NeoPopColors.Surface,
    bottomColor: Color = NeoPopColors.Black,
    borderColor: Color = NeoPopColors.Border,
    depth: Dp = 4.dp,
    cornerRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    val depthPx = with(LocalDensity.current) { depth.toPx() }

    Box(
        modifier.drawWithCache {
            val outerW = size.width
            val outerH = size.height
            val innerW = outerW - depthPx
            val innerH = outerH - depthPx

            onDrawBehind {
                // Right face
                val rightPath = Path().apply {
                    moveTo(innerW, 0f)
                    lineTo(outerW, depthPx)
                    lineTo(outerW, outerH)
                    lineTo(innerW, innerH)
                    close()
                }
                drawPath(rightPath, rightColor)

                // Bottom face
                val bottomPath = Path().apply {
                    moveTo(0f, innerH)
                    lineTo(innerW, innerH)
                    lineTo(outerW, outerH)
                    lineTo(depthPx, outerH)
                    close()
                }
                drawPath(bottomPath, bottomColor)
            }
        }
    ) {
        Box(
            Modifier
                .padding(end = depth, bottom = depth)
                .clip(RoundedCornerShape(cornerRadius))
                .background(surfaceColor)
                .drawWithCache {
                    onDrawBehind {
                        drawRect(borderColor, style = Stroke(width = 1f))
                    }
                }
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * Variant that emphasises a state colour (success/danger/warn) by tinting
 * the surface stroke and the side faces. The primary fill stays dark — only
 * the accents shift.
 */
@Composable
fun NeoPopAccentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    depth: Dp = 4.dp,
    cornerRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    NeoPopCard(
        modifier = modifier,
        surfaceColor = NeoPopColors.SurfaceHigh,
        rightColor = accent.copy(alpha = 0.4f),
        bottomColor = accent.copy(alpha = 0.7f),
        borderColor = accent,
        depth = depth,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        content = content
    )
}
