package com.offpay.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium money-rain easter egg.
 *
 * Drives a per-frame elapsed-millis tick (via [withFrameMillis]) and
 * recomputes every note's position on the fly, so notes start above the
 * screen, flutter down with a sine-wave drift, rotate as they fall, fade
 * out at the bottom, and the entire overlay reliably removes itself the
 * frame after the slowest note exits.
 *
 * Implemented entirely with [Canvas] paths — no emoji, no images, sharp
 * at any density. Three colour palettes per note give the rain visual
 * variety without looking random.
 */
@Composable
fun MoneyRainOverlay(onDone: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val noteW = with(density) { 88.dp.toPx() }
    val noteH = with(density) { 52.dp.toPx() }

    /** Per-note descriptor — initial conditions, palette, scale. */
    data class Note(
        val xFraction: Float,
        val swayAmplitude: Float,
        val swayPeriodMs: Float,
        val phaseOffset: Float,
        val rotationStart: Float,
        val spinDegPerSec: Float,
        val spawnDelayMs: Long,
        val fallDurationMs: Long,
        val scale: Float,
        val palette: Int
    )

    val notes = remember {
        List(30) { i ->
            Note(
                xFraction = Random.nextFloat(),
                swayAmplitude = Random.nextFloat() * 38f + 18f,
                swayPeriodMs = Random.nextFloat() * 800f + 1100f,
                phaseOffset = Random.nextFloat() * 6.28f,
                rotationStart = Random.nextFloat() * 60f - 30f,
                spinDegPerSec = (Random.nextFloat() * 200f + 80f) * if (Random.nextBoolean()) 1f else -1f,
                spawnDelayMs = Random.nextLong(0, 1_400),
                fallDurationMs = Random.nextLong(2_000, 3_400),
                scale = Random.nextFloat() * 0.35f + 0.78f,
                palette = i % 3
            )
        }
    }
    val totalLifetimeMs = (notes.maxOf { it.spawnDelayMs + it.fallDurationMs } + 200L)

    // Per-frame tick. Drives recomposition so every note's position is
    // recomputed each frame — guarantees they actually animate and exit
    // (the previous single-shot animateFloatAsState approach could be
    // pre-empted on slow devices and stick at progress=0).
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val startMs = remember { System.nanoTime() / 1_000_000L }
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        while (elapsedMs < totalLifetimeMs) {
            withFrameMillis { now ->
                elapsedMs = now - startMs
            }
        }
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            notes.forEach { note ->
                val noteElapsed = elapsedMs - note.spawnDelayMs
                if (noteElapsed <= 0L) return@forEach
                val local = (noteElapsed.toFloat() / note.fallDurationMs.toFloat())
                    .coerceIn(0f, 1f)

                val effectiveW = noteW * note.scale
                val effectiveH = noteH * note.scale

                val baseX = note.xFraction * (screenWidthPx - effectiveW).coerceAtLeast(0f)
                val swayX = note.swayAmplitude * sin(
                    note.phaseOffset + (noteElapsed.toFloat() / note.swayPeriodMs) * 6.28f
                )
                val x = baseX + swayX

                val startY = -effectiveH * 1.4f
                val endY = screenHeightPx + effectiveH * 0.5f
                val y = startY + (endY - startY) * local

                val rotation = note.rotationStart + note.spinDegPerSec * (noteElapsed / 1000f)

                // Soft fade-in at top, full opacity in the middle, fade-out
                // as it leaves the bottom.
                val alpha = when {
                    local < 0.06f -> local / 0.06f
                    local > 0.92f -> ((1f - local) / 0.08f).coerceIn(0f, 1f)
                    else -> 1f
                }

                rotate(
                    degrees = rotation,
                    pivot = Offset(x + effectiveW / 2f, y + effectiveH / 2f)
                ) {
                    drawCurrencyNote(
                        topLeft = Offset(x, y),
                        size = Size(effectiveW, effectiveH),
                        palette = note.palette,
                        alpha = alpha
                    )
                }
            }
        }
    }
}

/**
 * Draws a single rectangular currency note with a denomination glyph (₹),
 * a watermark, a subtle inset border, and OFFPAY microtext ridges.
 *
 * [palette] selects between three accent variants so the rain doesn't
 * look monotone:
 *  - 0: deep lime body, dark border, white type.
 *  - 1: cream/khaki body, dark border, dark type.
 *  - 2: muted teal body, dark border, white type.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurrencyNote(
    topLeft: Offset,
    size: Size,
    palette: Int,
    alpha: Float
) {
    val (body, border, ink, accent) = when (palette) {
        1 -> listOf(Color(0xFFE8DDB6), Color(0xFF38381E), Color(0xFF1A1A0F), Color(0xFF8AAB2C))
        2 -> listOf(Color(0xFF1F3F3A), Color(0xFF0A1A18), Color(0xFFE8F5EE), Color(0xFFC5F542))
        else -> listOf(Color(0xFF8AAB2C), Color(0xFF24300A), Color(0xFFFFFFFF), Color(0xFFC5F542))
    }
    val w = size.width
    val h = size.height
    val cx = topLeft.x
    val cy = topLeft.y

    // Drop shadow for depth
    drawRect(
        color = Color.Black.copy(alpha = 0.30f * alpha),
        topLeft = Offset(cx + 2f, cy + 4f),
        size = size
    )

    // Body
    drawRect(color = body.copy(alpha = alpha), topLeft = topLeft, size = size)

    // Outer border
    drawRect(
        color = border.copy(alpha = alpha),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = h * 0.045f)
    )

    // Inset hairline border, like real currency engraving
    val pad = h * 0.10f
    drawRect(
        color = border.copy(alpha = 0.6f * alpha),
        topLeft = Offset(cx + pad, cy + pad),
        size = Size(w - pad * 2f, h - pad * 2f),
        style = Stroke(width = h * 0.012f)
    )

    // Watermark circle on the right
    val wmCx = cx + w * 0.78f
    val wmCy = cy + h * 0.5f
    val wmR = h * 0.30f
    drawCircle(
        color = ink.copy(alpha = 0.20f * alpha),
        radius = wmR,
        center = Offset(wmCx, wmCy),
        style = Stroke(width = h * 0.025f)
    )
    // ₹ glyph stylised inside the watermark
    drawRupeeGlyph(
        center = Offset(wmCx, wmCy),
        size = wmR * 1.1f,
        color = ink.copy(alpha = 0.40f * alpha)
    )

    // Big "500" denomination on the left
    drawDenomination(
        topLeft = Offset(cx + w * 0.10f, cy + h * 0.22f),
        size = Size(w * 0.40f, h * 0.45f),
        color = ink.copy(alpha = alpha)
    )

    // ₹ small mark above the denomination
    drawRupeeGlyph(
        center = Offset(cx + w * 0.16f, cy + h * 0.16f),
        size = h * 0.14f,
        color = accent.copy(alpha = alpha)
    )

    // OFFPAY microtext line at the bottom — decorative ridges of varying
    // length to simulate microprint, not actual readable letters.
    val ridgeY = cy + h * 0.85f
    var offX = cx + w * 0.10f
    val ridgeColor = ink.copy(alpha = 0.55f * alpha)
    val ridges = listOf(0.04f, 0.025f, 0.05f, 0.02f, 0.045f, 0.03f, 0.05f, 0.025f)
    ridges.forEach { fr ->
        drawRect(
            color = ridgeColor,
            topLeft = Offset(offX, ridgeY - h * 0.02f),
            size = Size(w * fr, h * 0.04f)
        )
        offX += w * (fr + 0.012f)
    }
}

/** Stylised ₹ glyph at [center] with [size] cap-height. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRupeeGlyph(
    center: Offset,
    size: Float,
    color: Color
) {
    val left = center.x - size / 2f
    val top = center.y - size / 2f
    val stroke = size * 0.13f
    drawLine(
        color = color,
        start = Offset(left, top + size * 0.08f),
        end = Offset(left + size * 0.85f, top + size * 0.08f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(left, top + size * 0.30f),
        end = Offset(left + size * 0.85f, top + size * 0.30f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    val path = Path().apply {
        moveTo(left + size * 0.18f, top + size * 0.18f)
        cubicTo(
            left + size * 0.78f, top + size * 0.18f,
            left + size * 0.78f, top + size * 0.55f,
            left + size * 0.18f, top + size * 0.55f
        )
        lineTo(left + size * 0.82f, top + size * 1.0f)
    }
    drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/** Stylised "500" using rectangle-built digits so it reads at any size. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDenomination(
    topLeft: Offset,
    size: Size,
    color: Color
) {
    val glyphW = size.width / 3.4f
    val gap = (size.width - glyphW * 3f) / 2f
    drawDigit5(Offset(topLeft.x, topLeft.y), Size(glyphW, size.height), color)
    drawDigit0(Offset(topLeft.x + glyphW + gap, topLeft.y), Size(glyphW, size.height), color)
    drawDigit0(Offset(topLeft.x + (glyphW + gap) * 2f, topLeft.y), Size(glyphW, size.height), color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDigit5(
    topLeft: Offset,
    size: Size,
    color: Color
) {
    val s = size.height * 0.16f
    val x = topLeft.x; val y = topLeft.y; val w = size.width; val h = size.height
    drawRect(color, topLeft = Offset(x, y), size = Size(w, s))
    drawRect(color, topLeft = Offset(x, y), size = Size(s, h * 0.5f))
    drawRect(color, topLeft = Offset(x, y + h * 0.45f), size = Size(w, s))
    drawRect(color, topLeft = Offset(x + w - s, y + h * 0.5f), size = Size(s, h * 0.5f))
    drawRect(color, topLeft = Offset(x, y + h - s), size = Size(w, s))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDigit0(
    topLeft: Offset,
    size: Size,
    color: Color
) {
    val s = size.height * 0.16f
    val x = topLeft.x; val y = topLeft.y; val w = size.width; val h = size.height
    drawRect(color, topLeft = Offset(x, y), size = Size(w, s))
    drawRect(color, topLeft = Offset(x, y + h - s), size = Size(w, s))
    drawRect(color, topLeft = Offset(x, y), size = Size(s, h))
    drawRect(color, topLeft = Offset(x + w - s, y), size = Size(s, h))
}
