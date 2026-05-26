package com.offpay.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/**
 * The signature CRED NeoPOP button.
 *
 * Five visible surfaces give it the 3D pop-out look:
 *  - Top face (the main coloured fill, holds the content)
 *  - Right face (slanted parallelogram, darker)
 *  - Bottom face (slanted parallelogram, darkest)
 *  - Top-right slant (where right face meets the top face's top-right corner)
 *  - Bottom-right slant (where bottom and right faces meet at the bottom-right outer corner)
 *
 * The slants emerge naturally from the parallelogram geometry — top-back-right,
 * bottom-back-right, and bottom-back-left form the rear-most rectangle, and
 * the lines from the front-face corners to those back corners ARE the slants.
 *
 * On press, the top face translates +depth on x and +depth on y. The
 * parallelograms collapse to zero area (top-front-right meets top-back-right,
 * bottom-front-left meets bottom-back-left), giving the "pressed into the
 * page" feel. Spring animation: low-bounce, high-stiffness — snappy, no
 * overshoot.
 *
 * Haptic: [HapticFeedbackConstants.VIRTUAL_KEY] fires on every click.
 */
@Composable
fun NeoPopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeoPopColors.Accent,
    rightColor: Color = NeoPopColors.AccentDim,
    bottomColor: Color = NeoPopColors.AccentDeep,
    contentColor: Color = NeoPopColors.Black,
    depth: Dp = 8.dp,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "neopop_press"
    )

    val depthPx = with(LocalDensity.current) { depth.toPx() }
    val displayColor = if (enabled) color else color.copy(alpha = 0.4f)
    val displayRight = if (enabled) rightColor else rightColor.copy(alpha = 0.3f)
    val displayBottom = if (enabled) bottomColor else bottomColor.copy(alpha = 0.3f)

    Box(
        modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .drawWithCache {
                // Outer rect = (W, H) including the side faces.
                // Inner top face un-pressed = (W - depthPx, H - depthPx).
                val outerW = size.width
                val outerH = size.height
                val innerW = outerW - depthPx
                val innerH = outerH - depthPx

                onDrawBehind {
                    val p = pressProgress
                    val fx = depthPx * p   // front-face x offset (top-left)
                    val fy = depthPx * p   // front-face y offset (top-left)

                    // ── Right face parallelogram ──
                    // top-front-right → top-back-right → bottom-back-right → bottom-front-right
                    val rightPath = Path().apply {
                        moveTo(innerW + fx, fy)
                        lineTo(outerW, depthPx)
                        lineTo(outerW, outerH)
                        lineTo(innerW + fx, innerH + fy)
                        close()
                    }
                    drawPath(rightPath, displayRight)

                    // ── Bottom face parallelogram ──
                    // bottom-front-left → bottom-front-right → bottom-back-right → bottom-back-left
                    val bottomPath = Path().apply {
                        moveTo(fx, innerH + fy)
                        lineTo(innerW + fx, innerH + fy)
                        lineTo(outerW, outerH)
                        lineTo(depthPx, outerH)
                        close()
                    }
                    drawPath(bottomPath, displayBottom)
                }
            }
            .padding(end = depth, bottom = depth)
    ) {
        // ── Top face ──
        // Animates +depth on x and y when pressed; same colour fill carries
        // the content (icon + label).
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = depthPx * pressProgress
                    translationY = depthPx * pressProgress
                }
                .background(displayColor)
                .padding(contentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = NeoPopType.LabelLarge,
                    color = contentColor
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Primary CTA — lime accent on black. Use sparingly; one per screen.
 */
@Composable
fun NeoPopPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    depth: Dp = 8.dp
) {
    NeoPopButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        color = NeoPopColors.Accent,
        rightColor = NeoPopColors.AccentDim,
        bottomColor = NeoPopColors.AccentDeep,
        contentColor = NeoPopColors.Black,
        enabled = enabled,
        depth = depth,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

/**
 * Secondary CTA — white-on-black outlined. No 3D pop, just a sharp stroked
 * rectangle that snaps inward on press.
 */
@Composable
fun NeoPopSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "neopop_secondary"
    )
    val borderColor = if (enabled) NeoPopColors.TextPrimary else NeoPopColors.TextMuted
    val labelColor = if (enabled) NeoPopColors.TextPrimary else NeoPopColors.TextMuted

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .drawBehind {
                drawRect(
                    color = borderColor,
                    style = Stroke(width = 2f),
                    topLeft = Offset(0f, 0f)
                )
            }
            .padding(contentPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = NeoPopType.LabelLarge,
                color = labelColor
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Danger CTA — red 3D pop. */
@Composable
fun NeoPopDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    depth: Dp = 8.dp
) {
    NeoPopButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        color = NeoPopColors.Danger,
        rightColor = NeoPopColors.DangerDim,
        bottomColor = NeoPopColors.DangerDeep,
        contentColor = NeoPopColors.TextPrimary,
        enabled = enabled,
        depth = depth,
        leadingIcon = leadingIcon
    )
}

/** Outlined danger — for destructive secondary actions. */
@Composable
fun NeoPopDangerOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "neopop_danger_outline"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .drawBehind { drawRect(NeoPopColors.Danger, style = Stroke(width = 2f)) }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            style = NeoPopType.LabelLarge,
            color = NeoPopColors.Danger,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
