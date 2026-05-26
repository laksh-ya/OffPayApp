package com.offpay.app.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.offpay.app.domain.SessionState
import com.offpay.app.presentation.ui.components.NeoPopAccentCard
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Visually-rich result cards for the Pay flow.
 *
 * The design intent is to keep this distinct from typical UPI apps (which
 * rely on rounded check-mark Lottie loops). Instead we lean fully into the
 * NeoPOP language already used in this app:
 *
 *  - Sharp square hero (no circles).
 *  - Stroke-drawn check / cross — animated reveal, not a static glyph.
 *  - Geometric square "confetti" burst (lime + white tiles), not round dots.
 *  - Glitch-shake + scan-lines on failure, lifted from arcade/CRT vocab.
 *  - Staggered text reveal so the eye reads label → result → CTA in order.
 *
 * All animations are driven from a single Animatable timeline (0..1) so the
 * tuning stays in one place; sub-segments slice that timeline.
 */

private const val ANIM_SUCCESS_DURATION_MS = 1700
private const val ANIM_FAILED_DURATION_MS = 1100
private const val SHAKE_DURATION_MS = 460

// ─── Success ────────────────────────────────────────────────────────────────

@Composable
internal fun SessionSuccessCard(state: SessionState.Success, onDone: () -> Unit) {
    val view = LocalView.current
    val timeline = remember { Animatable(0f) }
    val particles = remember { generateConfettiParticles() }

    LaunchedEffect(Unit) {
        // Double-tap haptic — first beat on entry, second when the check
        // stroke locks in. Mirrors the gesture of tapping a stamp twice.
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        launch {
            kotlinx.coroutines.delay(420)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        timeline.animateTo(1f, tween(ANIM_SUCCESS_DURATION_MS, easing = LinearEasing))
    }

    val t = timeline.value
    val cardEnter = easeOutCubic((t / 0.18f).coerceIn(0f, 1f))
    val iconScale = easeOutBack(((t - 0.05f) / 0.20f).coerceIn(0f, 1f))
    val borderSweep = ((t - 0.18f) / 0.22f).coerceIn(0f, 1f)
    val checkProgress = ((t - 0.36f) / 0.20f).coerceIn(0f, 1f)
    val confettiProgress = ((t - 0.46f) / 0.54f).coerceIn(0f, 1f)
    val labelAlpha = ((t - 0.55f) / 0.18f).coerceIn(0f, 1f)
    val resultAlpha = ((t - 0.68f) / 0.20f).coerceIn(0f, 1f)
    val ctaAlpha = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)

    val haloPulse by rememberInfiniteTransition(label = "successHalo").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "successHaloAlpha"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - cardEnter) * 56f
                    alpha = cardEnter
                }
        ) {
            NeoPopAccentCard(
                accent = NeoPopColors.Success,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Hero zone — confetti rendered behind, hero square in front.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawSuccessConfetti(
                                progress = confettiProgress,
                                particles = particles,
                                originX = size.height * 0.5f,
                                originY = size.height * 0.5f
                            )
                        }
                        Box(
                            Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawSuccessHero(
                                    borderSweep = borderSweep,
                                    checkProgress = checkProgress,
                                    haloAlpha = haloPulse * (if (t > 0.55f) 1f else 0f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "PAYMENT COMPLETE",
                        style = NeoPopType.LabelSmall.copy(letterSpacing = 0.18.em),
                        color = NeoPopColors.Success,
                        modifier = Modifier.alpha(labelAlpha)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.resultText,
                        style = NeoPopType.HeadlineLarge,
                        color = NeoPopColors.TextPrimary,
                        modifier = Modifier.alpha(resultAlpha)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.alpha(ctaAlpha)) {
            NeoPopPrimaryButton(
                text = "Done",
                onClick = { if (ctaAlpha > 0.5f) onDone() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Failed ─────────────────────────────────────────────────────────────────

@Composable
internal fun SessionFailedCard(
    state: SessionState.Failed,
    onRetry: () -> Unit,
    onWhyFailed: () -> Unit
) {
    val view = LocalView.current
    val shake = remember { Animatable(0f) }
    val timeline = remember { Animatable(0f) }
    val density = LocalDensity.current
    val shakePxMax = with(density) { 14.dp.toPx() }

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        launch {
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = SHAKE_DURATION_MS
                    0f at 0
                    -shakePxMax at 50
                    shakePxMax * 0.85f at 110
                    -shakePxMax * 0.65f at 170
                    shakePxMax * 0.45f at 230
                    -shakePxMax * 0.25f at 290
                    shakePxMax * 0.12f at 360
                    0f at SHAKE_DURATION_MS
                }
            )
        }
        timeline.animateTo(1f, tween(ANIM_FAILED_DURATION_MS, easing = LinearEasing))
    }

    val t = timeline.value
    val cardEnter = easeOutCubic((t / 0.18f).coerceIn(0f, 1f))
    val iconScale = easeOutBack(((t - 0.04f) / 0.22f).coerceIn(0f, 1f))
    val slashProgress = ((t - 0.22f) / 0.30f).coerceIn(0f, 1f)
    val labelAlpha = ((t - 0.48f) / 0.18f).coerceIn(0f, 1f)
    val messageAlpha = ((t - 0.62f) / 0.18f).coerceIn(0f, 1f)
    val secondaryAlpha = ((t - 0.78f) / 0.16f).coerceIn(0f, 1f)
    val ctaAlpha = ((t - 0.86f) / 0.14f).coerceIn(0f, 1f)

    val glitchPhase by rememberInfiniteTransition(label = "failGlitch").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "failGlitchPhase"
    )
    // Slow danger-border breath, comes alive after the entry shake settles.
    val borderBreath by rememberInfiniteTransition(label = "failBreath").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "failBreathAlpha"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .graphicsLayer { translationX = shake.value },
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - cardEnter) * 24f
                    alpha = cardEnter
                }
        ) {
            NeoPopAccentCard(
                accent = NeoPopColors.Danger,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Subtle scan-line overlay across the hero area.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawGlitchScanLines(
                                phase = glitchPhase,
                                visibility = (t.coerceAtMost(0.75f) / 0.75f)
                            )
                        }
                        Box(
                            Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawFailedHero(
                                    slashProgress = slashProgress,
                                    glitchPhase = glitchPhase,
                                    borderAlpha = borderBreath * (if (t > 0.5f) 1f else 0f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "PAYMENT FAILED",
                        style = NeoPopType.LabelSmall.copy(letterSpacing = 0.18.em),
                        color = NeoPopColors.Danger,
                        modifier = Modifier.alpha(labelAlpha)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = NeoPopType.HeadlineLarge,
                        color = NeoPopColors.TextPrimary,
                        modifier = Modifier.alpha(messageAlpha)
                    )
                    if (state.resultText.isNotBlank() && state.resultText != state.message) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.resultText,
                            style = NeoPopType.BodyMedium,
                            color = NeoPopColors.TextSecondary,
                            modifier = Modifier.alpha(secondaryAlpha)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Why did this fail?",
                        style = NeoPopType.LabelMedium,
                        color = NeoPopColors.Accent,
                        modifier = Modifier
                            .alpha(secondaryAlpha)
                            .clickable(enabled = secondaryAlpha > 0.5f) { onWhyFailed() }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.alpha(ctaAlpha)) {
            NeoPopPrimaryButton(
                text = "Try Again",
                onClick = { if (ctaAlpha > 0.5f) onRetry() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Drawing primitives ─────────────────────────────────────────────────────

private fun DrawScope.drawSuccessHero(
    borderSweep: Float,
    checkProgress: Float,
    haloAlpha: Float
) {
    val w = size.width
    val h = size.height

    // Halo: thin lime square outline behind the hero, breathing on entry.
    if (haloAlpha > 0f) {
        val pad = -10f
        drawRect(
            color = NeoPopColors.Success.copy(alpha = 0.28f * haloAlpha),
            topLeft = Offset(pad, pad),
            size = Size(w - pad * 2f, h - pad * 2f),
            style = Stroke(width = 2.5f)
        )
        val pad2 = -22f * haloAlpha
        drawRect(
            color = NeoPopColors.Success.copy(alpha = 0.10f * haloAlpha),
            topLeft = Offset(pad2, pad2),
            size = Size(w - pad2 * 2f, h - pad2 * 2f),
            style = Stroke(width = 1.5f)
        )
    }

    // Solid lime fill — the hero square.
    drawRect(color = NeoPopColors.Success, size = Size(w, h))

    // Border sweep: clockwise progressive stroke around the perimeter using
    // four lines, drawn in sequence (top → right → bottom → left).
    if (borderSweep > 0f) {
        val total = (w + h) * 2f
        var remaining = total * borderSweep
        val strokeColor = NeoPopColors.SuccessDeep
        val sw = 4f

        // Top edge (left → right)
        val topLen = w
        val drawnTop = remaining.coerceAtMost(topLen)
        drawLine(
            strokeColor,
            Offset(0f, sw / 2f),
            Offset(drawnTop, sw / 2f),
            sw
        )
        remaining -= drawnTop
        // Right edge (top → bottom)
        if (remaining > 0f) {
            val rightLen = h
            val drawnRight = remaining.coerceAtMost(rightLen)
            drawLine(
                strokeColor,
                Offset(w - sw / 2f, 0f),
                Offset(w - sw / 2f, drawnRight),
                sw
            )
            remaining -= drawnRight
        }
        // Bottom edge (right → left)
        if (remaining > 0f) {
            val bottomLen = w
            val drawnBottom = remaining.coerceAtMost(bottomLen)
            drawLine(
                strokeColor,
                Offset(w, h - sw / 2f),
                Offset(w - drawnBottom, h - sw / 2f),
                sw
            )
            remaining -= drawnBottom
        }
        // Left edge (bottom → top)
        if (remaining > 0f) {
            val leftLen = h
            val drawnLeft = remaining.coerceAtMost(leftLen)
            drawLine(
                strokeColor,
                Offset(sw / 2f, h),
                Offset(sw / 2f, h - drawnLeft),
                sw
            )
        }
    }

    // Check stroke — two segments drawn in sequence.
    val a = Offset(w * 0.20f, h * 0.52f)
    val b = Offset(w * 0.42f, h * 0.72f)
    val c = Offset(w * 0.80f, h * 0.30f)
    val seg1 = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
    val seg2 = hypot((c.x - b.x).toDouble(), (c.y - b.y).toDouble()).toFloat()
    val totalLen = seg1 + seg2
    val drawn = totalLen * checkProgress
    if (drawn > 0f) {
        val checkColor = NeoPopColors.Black
        val sw = 7f
        val first = drawn.coerceAtMost(seg1)
        val firstT = if (seg1 > 0f) first / seg1 else 0f
        drawLine(
            checkColor,
            a,
            Offset(a.x + (b.x - a.x) * firstT, a.y + (b.y - a.y) * firstT),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        if (drawn > seg1) {
            val secondT = ((drawn - seg1) / seg2).coerceIn(0f, 1f)
            drawLine(
                checkColor,
                b,
                Offset(b.x + (c.x - b.x) * secondT, b.y + (c.y - b.y) * secondT),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawFailedHero(
    slashProgress: Float,
    glitchPhase: Float,
    borderAlpha: Float
) {
    val w = size.width
    val h = size.height

    // Filled danger square with a deeper inner band — gives weight.
    drawRect(color = NeoPopColors.Danger, size = Size(w, h))
    drawRect(
        color = NeoPopColors.DangerDeep.copy(alpha = 0.55f),
        topLeft = Offset(w * 0.08f, h * 0.08f),
        size = Size(w * 0.84f, h * 0.84f)
    )

    // Outer breathing border, kicks in after the slashes.
    if (borderAlpha > 0f) {
        drawRect(
            color = NeoPopColors.Danger.copy(alpha = 0.55f * borderAlpha),
            topLeft = Offset(-8f, -8f),
            size = Size(w + 16f, h + 16f),
            style = Stroke(width = 2f)
        )
    }

    // Glitch flicker bar — narrow horizontal slice that jitters across
    // the icon every cycle. Triggers a micro-displacement only between
    // phase 0.0..0.12 and 0.55..0.62 so it feels intermittent.
    val flicker1 = (glitchPhase >= 0f && glitchPhase < 0.10f)
    val flicker2 = (glitchPhase >= 0.52f && glitchPhase < 0.60f)
    if (flicker1 || flicker2) {
        val y = (if (flicker1) 0.30f else 0.65f) * h
        drawRect(
            color = NeoPopColors.TextPrimary.copy(alpha = 0.22f),
            topLeft = Offset(0f, y),
            size = Size(w, h * 0.06f)
        )
    }

    // Diagonal slash 1: top-left to bottom-right
    val pad = w * 0.24f
    val a1 = Offset(pad, pad)
    val a2 = Offset(w - pad, h - pad)
    val b1 = Offset(w - pad, pad)
    val b2 = Offset(pad, h - pad)

    val slashColor = NeoPopColors.TextPrimary
    val sw = 7f

    val firstSegT = (slashProgress / 0.55f).coerceIn(0f, 1f)
    val secondSegT = ((slashProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    if (firstSegT > 0f) {
        drawLine(
            slashColor,
            a1,
            Offset(a1.x + (a2.x - a1.x) * firstSegT, a1.y + (a2.y - a1.y) * firstSegT),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
    if (secondSegT > 0f) {
        drawLine(
            slashColor,
            b1,
            Offset(b1.x + (b2.x - b1.x) * secondSegT, b1.y + (b2.y - b1.y) * secondSegT),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawGlitchScanLines(phase: Float, visibility: Float) {
    if (visibility <= 0f) return
    val w = size.width
    val h = size.height
    // Three thin horizontal lines drifting downward, looping.
    val lineCount = 3
    val baseAlpha = 0.10f * visibility
    for (i in 0 until lineCount) {
        val offset = ((phase + i / lineCount.toFloat()) % 1f)
        val y = offset * h
        drawRect(
            color = NeoPopColors.Danger.copy(alpha = baseAlpha),
            topLeft = Offset(0f, y),
            size = Size(w, 1.5f)
        )
    }
    // Occasional flicker line, quick on/off near the start of cycle.
    if (phase < 0.05f) {
        val y = h * 0.42f
        drawRect(
            color = NeoPopColors.TextPrimary.copy(alpha = 0.18f * visibility),
            topLeft = Offset(0f, y),
            size = Size(w, 1f)
        )
    }
}

// ─── Confetti (geometric square burst) ──────────────────────────────────────

private data class ConfettiParticle(
    val angleDeg: Float,
    val speed: Float,
    val sizePx: Float,
    val color: Color,
    val rotSpeed: Float,
    val phase: Float
)

private fun generateConfettiParticles(count: Int = 26): List<ConfettiParticle> {
    val rng = java.util.Random(42L) // deterministic so previews/test stays stable
    val palette = listOf(
        NeoPopColors.Accent,
        NeoPopColors.Success,
        NeoPopColors.TextPrimary,
        NeoPopColors.SuccessDim
    )
    return List(count) {
        // Bias upward & to the right (icon sits on the left edge of the card).
        val baseAngle = -55f + (rng.nextFloat() * 110f - 20f)
        ConfettiParticle(
            angleDeg = baseAngle,
            speed = 0.55f + rng.nextFloat() * 1.25f,
            sizePx = 4f + rng.nextFloat() * 8f,
            color = palette[rng.nextInt(palette.size)],
            rotSpeed = (rng.nextFloat() - 0.5f) * 720f,
            phase = rng.nextFloat() * 0.18f
        )
    }
}

private fun DrawScope.drawSuccessConfetti(
    progress: Float,
    particles: List<ConfettiParticle>,
    originX: Float,
    originY: Float
) {
    if (progress <= 0f) return
    val maxDistPx = size.width * 0.85f
    for (p in particles) {
        val pt = ((progress - p.phase) / (1f - p.phase)).coerceIn(0f, 1f)
        if (pt <= 0f) continue
        val rad = (p.angleDeg * PI / 180f).toFloat()
        val dist = pt * maxDistPx * p.speed
        val gravity = pt * pt * 80f
        val x = originX + cos(rad) * dist
        val y = originY + sin(rad) * dist + gravity
        // Quadratic fade — particles linger then drop off near the end.
        val alpha = (1f - pt * pt).coerceAtLeast(0f)
        rotate(degrees = p.rotSpeed * pt, pivot = Offset(x, y)) {
            drawRect(
                color = p.color.copy(alpha = alpha),
                topLeft = Offset(x - p.sizePx / 2f, y - p.sizePx / 2f),
                size = Size(p.sizePx, p.sizePx)
            )
        }
    }
}

// ─── Easing helpers ─────────────────────────────────────────────────────────

private fun easeOutCubic(t: Float): Float {
    val u = (1f - t.coerceIn(0f, 1f))
    return 1f - u * u * u
}

/** Slight overshoot that pops, then settles — used for the hero square. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t.coerceIn(0f, 1f) - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}
