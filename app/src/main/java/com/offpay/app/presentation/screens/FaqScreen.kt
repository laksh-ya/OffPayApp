package com.offpay.app.presentation.screens

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.offpay.app.R
import com.offpay.app.presentation.permissions.openAccessibilitySettings
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopSecondaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

// ─── Hooks for the user to drop in real assets later ──────────────────────────

/**
 * BHIM walkthrough screenshots. Wired to the bundled drawables now —
 * tap-fingers from the original BHIM screenshots, 738×1600 portrait, drawn
 * scaled-to-fit so they stay readable at typical phone widths.
 */
private val step1ImageRes: Int? = R.drawable.bhim_step1
private val step2ImageRes: Int? = R.drawable.bhim_step2
private val step3ImageRes: Int? = R.drawable.bhim_step3

/**
 * Tutorial video URL. Tapping the "Watch the tutorial" CTA fires
 * `Intent.ACTION_VIEW` on this URL.
 */
private val TUTORIAL_VIDEO_URL: String? = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

/**
 * Help/FAQ screen. Reachable from:
 *  - Settings → "Help & FAQ".
 *  - Onboarding → "Need help?" link (future).
 *  - Error states → "Why did this fail?" link.
 *
 * Sections (in order):
 *   1. What is *99# UPI
 *   2. Enable *99# in BHIM (the user's #1 setup blocker — 3-step walkthrough)
 *   3. Enable OffPay accessibility (with Android 13+ "Restricted Settings" workaround)
 *   4. Three modes
 *   5. Which carriers work
 *   6. How the overlay works
 *   7. Is my PIN safe
 *   8. What if it fails
 *   9. Video tutorial (placeholder embed)
 */
@Composable
fun FaqScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CornerCloseButton(onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "HELP",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(40.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            HeroBlurb()

            Spacer(Modifier.height(24.dp))

            FaqSection(
                category = "WHAT IS *99# UPI",
                body = "*99# is India's USSD-based UPI service. It works without internet — " +
                    "you dial a code, your bank shows menus through SMS-style dialogs, and you " +
                    "reply to send money. Works on any phone with a cellular signal."
            )
            Spacer(Modifier.height(16.dp))

            BhimWalkthroughCard()
            Spacer(Modifier.height(16.dp))

            AccessibilitySetupCard(
                onOpenSettings = { openAccessibilitySettings(context) }
            )
            Spacer(Modifier.height(16.dp))

            ThreeModesCard()
            Spacer(Modifier.height(16.dp))

            FaqSection(
                category = "WHICH CARRIERS WORK",
                body = "Airtel, Vodafone (Vi), and BSNL — yes. Jio — no. That's a technical " +
                    "limitation on Jio's network, not OffPay's fault."
            )
            Spacer(Modifier.height(16.dp))

            FaqSection(
                category = "HOW THE OVERLAY WORKS",
                body = "OffPay drives the carrier's USSD dialog automatically using Android's " +
                    "accessibility service. We never see your PIN — it's typed locally on your " +
                    "device, never stored, and never sent to any server."
            )
            Spacer(Modifier.height(16.dp))

            FaqSection(
                category = "IS MY PIN SAFE?",
                body = "Yes. Your PIN is held in volatile memory only, masked in any logs as " +
                    "••••, cleared within 500ms of session end, never persisted, and never shared."
            )
            Spacer(Modifier.height(16.dp))

            FaqSection(
                category = "WHAT IF IT FAILS?",
                body = "We surface the carrier's exact error message. If your bank isn't linked " +
                    "to *99#, we route you to onboarding instructions. A 25-second hard-cap " +
                    "timeout prevents stuck sessions."
            )

            Spacer(Modifier.height(16.dp))

            VideoTutorialCard(context = context)

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HeroBlurb() {
    val text = buildAnnotatedString {
        append("we know USSD is weird. here's how ")
        withStyle(SpanStyle(color = NeoPopColors.Accent)) {
            append("OffPay")
        }
        append(" makes it work.")
    }
    Text(
        text = text,
        style = NeoPopType.DisplayMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = NeoPopColors.TextPrimary
    )
}

@Composable
private fun FaqSection(category: String, body: String) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = category,
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = NeoPopType.BodyLarge,
                color = NeoPopColors.TextSecondary
            )
        }
    }
}

/**
 * BHIM walkthrough — the most-asked setup question. Three numbered steps
 * with a 16:9 image slot below each. Image slots fall back to a
 * lime-bordered "step image" placeholder until real screenshots are wired
 * via [step1ImageRes] / [step2ImageRes] / [step3ImageRes].
 */
@Composable
private fun BhimWalkthroughCard() {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "ENABLE *99# IN BHIM",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "If *99# fails or your bank isn't found, this is the fix. Done it once? You're set.",
                style = NeoPopType.BodyMedium,
                color = NeoPopColors.TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            NumberedStep(
                index = 1,
                body = "Open BHIM app → tap your profile avatar (initials in top-left)."
            )
            ImageSlot(res = step1ImageRes)
            Spacer(Modifier.height(14.dp))

            NumberedStep(
                index = 2,
                body = "Scroll down → tap Settings."
            )
            ImageSlot(res = step2ImageRes)
            Spacer(Modifier.height(14.dp))

            NumberedStep(
                index = 3,
                body = "Find \"USSD service (*99#)\" under Account settings → toggle it ON."
            )
            ImageSlot(res = step3ImageRes)
        }
    }
}

@Composable
private fun AccessibilitySetupCard(onOpenSettings: () -> Unit) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "ENABLE OFFPAY ACCESSIBILITY",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(12.dp))
            NumberedStep(
                index = 1,
                body = "Open phone Settings → Accessibility → Installed apps (or Downloaded services)."
            )
            Spacer(Modifier.height(10.dp))
            NumberedStep(
                index = 2,
                body = "Find OffPay → toggle ON. Confirm the dialog."
            )
            Spacer(Modifier.height(10.dp))
            NumberedStep(
                index = 3,
                body = "If toggle is greyed out (Android 13+): long-press OffPay icon → App info → tap the ⋮ menu top-right → Allow restricted settings. Then return and toggle.",
                emphasised = true
            )
            Spacer(Modifier.height(10.dp))
            NumberedStep(
                index = 4,
                body = "Come back to OffPay. The \"Accessibility\" tile in Settings should now show GRANTED."
            )
            Spacer(Modifier.height(16.dp))
            NeoPopSecondaryButton(
                text = "Open Accessibility Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ThreeModesCard() {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "THREE MODES",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(12.dp))
            ModeRow(
                name = "Auto",
                desc = "Default. Pays automatically. Carrier dialog hidden behind OffPay's overlay."
            )
            Spacer(Modifier.height(12.dp))
            ModeRow(
                name = "Advanced",
                desc = "Pays automatically. Small progress bar at top; you watch the carrier dialog work."
            )
            Spacer(Modifier.height(12.dp))
            ModeRow(
                name = "Manual",
                desc = "Copies UPI ID and opens dialer. You enter the rest yourself. No accessibility needed."
            )
        }
    }
}

@Composable
private fun VideoTutorialCard(context: android.content.Context) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "VIDEO TUTORIAL",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(12.dp))
            val url = TUTORIAL_VIDEO_URL
            if (url != null) {
                Text(
                    text = "Watch the 60-second walkthrough.",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                NeoPopSecondaryButton(
                    text = "Watch the tutorial",
                    leadingIcon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(NeoPopColors.Surface)
                        .drawBehind {
                            drawRect(
                                color = NeoPopColors.Accent.copy(alpha = 0.4f),
                                style = Stroke(width = 1f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video tutorial coming soon",
                        style = NeoPopType.LabelMedium,
                        color = NeoPopColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(name: String, desc: String) {
    Column {
        Text(
            text = name,
            style = NeoPopType.TitleMedium,
            color = NeoPopColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = desc,
            style = NeoPopType.BodyMedium,
            color = NeoPopColors.TextSecondary
        )
    }
}

/**
 * Numbered step row. The number sits in a small lime square; the body is
 * regular body text (or [NeoPopColors.TextPrimary] when [emphasised]).
 */
@Composable
private fun NumberedStep(index: Int, body: String, emphasised: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(24.dp)
                .background(NeoPopColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Black
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = body,
            style = NeoPopType.BodyMedium,
            color = if (emphasised) NeoPopColors.TextPrimary else NeoPopColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ImageSlot(res: Int?) {
    Spacer(Modifier.height(10.dp))
    if (res != null) {
        // Real screenshot: scale-to-fit, capped at 480dp tall so the portrait
        // BHIM screenshots don't dominate the screen on small devices.
        Image(
            painter = painterResource(id = res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(NeoPopColors.Surface)
                .drawBehind {
                    drawRect(
                        color = NeoPopColors.Accent.copy(alpha = 0.4f),
                        style = Stroke(width = 1f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "step image",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.TextMuted
            )
        }
    }
}

/**
 * Corner X close button used by the FAQ header. 40dp tap target with a
 * subtle 12% white hover ring + 0.92 scale on press.
 */
@Composable
private fun CornerCloseButton(onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "close_scale"
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawRect(
                    color = NeoPopColors.TextPrimary.copy(alpha = 0.12f),
                    style = Stroke(width = 1f)
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = NeoPopColors.TextPrimary
        )
    }
}
