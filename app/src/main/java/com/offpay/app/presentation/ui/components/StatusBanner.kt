package com.offpay.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/**
 * Bar that surfaces a permission/setup warning at the top of a screen.
 * Tinted left edge (3dp accent line) signals severity at a glance; the body
 * shows title + supporting text and an inline action.
 */
@Composable
fun StatusBanner(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    severity: BannerSeverity = BannerSeverity.Warn,
    icon: ImageVector = Icons.Default.Warning
) {
    val accent = when (severity) {
        BannerSeverity.Warn -> NeoPopColors.Warn
        BannerSeverity.Danger -> NeoPopColors.Danger
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(NeoPopColors.SurfaceHigh)
            .drawBehind {
                // Left edge tint
                drawRect(
                    color = accent,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(width = 3.dp.toPx(), height = size.height)
                )
                // Hairline border around the rest
                drawLine(
                    color = NeoPopColors.Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = NeoPopColors.Border,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = NeoPopType.LabelMedium,
                    color = accent
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = message,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            NeoPopFixButton(text = actionLabel, color = accent, onClick = onAction)
        }
    }
}

/**
 * Mini NeoPOP "Fix" CTA. Lime (or warn) front face with a 2dp side+bottom
 * face that visibly collapses on press — same model as [NeoPopButton] but
 * shrunk down for inline placement next to a banner.
 */
@Composable
private fun NeoPopFixButton(text: String, color: Color, onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "fix_press"
    )

    val depth = 2.dp
    val depthPx = with(LocalDensity.current) { depth.toPx() }
    val rightColor = color.copy(alpha = 0.55f)
    val bottomColor = color.copy(alpha = 0.85f)

    Box(
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .drawWithCache {
                val outerW = size.width
                val outerH = size.height
                val innerW = outerW - depthPx
                val innerH = outerH - depthPx
                onDrawBehind {
                    val p = pressProgress
                    val fx = depthPx * p
                    val fy = depthPx * p
                    drawPath(
                        Path().apply {
                            moveTo(innerW + fx, fy)
                            lineTo(outerW, depthPx)
                            lineTo(outerW, outerH)
                            lineTo(innerW + fx, innerH + fy)
                            close()
                        },
                        rightColor
                    )
                    drawPath(
                        Path().apply {
                            moveTo(fx, innerH + fy)
                            lineTo(innerW + fx, innerH + fy)
                            lineTo(outerW, outerH)
                            lineTo(depthPx, outerH)
                            close()
                        },
                        bottomColor
                    )
                }
            }
            .padding(end = depth, bottom = depth)
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = depthPx * pressProgress
                    translationY = depthPx * pressProgress
                }
                .background(color)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.Black
            )
        }
    }
}

enum class BannerSeverity { Warn, Danger }
