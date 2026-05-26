package com.offpay.app.presentation.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NeoPOP palette. CRED's signature design language: black canvas, sharp
 * surfaces, high-contrast text, lime accent reserved for primary action.
 *
 * Use [Accent] sparingly — only for the single primary CTA on a screen.
 * Secondary actions use white-on-black outlined buttons.
 */
object NeoPopColors {
    // Surface
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0E0F12)
    val SurfaceHigh = Color(0xFF16181D)
    val SurfaceHigher = Color(0xFF1F2229)

    // Borders
    val Border = Color(0xFF2A2D34)
    val BorderStrong = Color(0xFF3A3E47)

    // Text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9BA1A8)
    val TextMuted = Color(0xFF5A6068)

    // Accent (NeoPOP signature lime)
    val Accent = Color(0xFFC5F542)
    val AccentDim = Color(0xFF8AAB2C)
    val AccentDeep = Color(0xFF5B7218)

    // Status
    val Danger = Color(0xFFFF4D4D)
    val DangerDim = Color(0xFFB23636)
    val DangerDeep = Color(0xFF6B1F1F)

    val Success = Color(0xFF00D26A)
    val SuccessDim = Color(0xFF008C47)
    val SuccessDeep = Color(0xFF00532B)

    val Warn = Color(0xFFFFB020)
    val WarnDim = Color(0xFFB37000)

    val Overlay = Color(0xCC000000)
}
