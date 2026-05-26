package com.offpay.app.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * NeoPOP typography. Default sans for body, monospace for amounts/codes.
 * Heavy use of bold weights and uppercase letter-spacing on labels for the
 * CRED-style mechanical feel.
 *
 * We don't bundle custom fonts (no asset weight added) — system fallbacks
 * do an honest job at this weight pyramid, and Inter/Space Grotesk would
 * pull in a few hundred KB of OTF.
 */
object NeoPopType {
    val Display = FontFamily.SansSerif
    val Body = FontFamily.SansSerif
    val MonoFamily = FontFamily.Monospace

    val DisplayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        letterSpacing = (-0.04).em
    )
    val DisplayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.03).em
    )
    val DisplaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em
    )
    val HeadlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    )
    val TitleLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    )
    val TitleMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    )
    val BodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
    val BodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    val BodySmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
    val LabelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.08.em
    )
    val LabelMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.1.em
    )
    val LabelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.14.em
    )
    val Mono = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    val MonoLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.02).em
    )
}

val NeoPopTypography = Typography(
    displayLarge = NeoPopType.DisplayLarge,
    displayMedium = NeoPopType.DisplayMedium,
    displaySmall = NeoPopType.DisplaySmall,
    headlineLarge = NeoPopType.HeadlineLarge,
    titleLarge = NeoPopType.TitleLarge,
    titleMedium = NeoPopType.TitleMedium,
    bodyLarge = NeoPopType.BodyLarge,
    bodyMedium = NeoPopType.BodyMedium,
    bodySmall = NeoPopType.BodySmall,
    labelLarge = NeoPopType.LabelLarge,
    labelMedium = NeoPopType.LabelMedium,
    labelSmall = NeoPopType.LabelSmall
)
