package com.offpay.app.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.domain.OperationMode
import com.offpay.app.domain.SessionState
import com.offpay.app.presentation.BalanceResult
import com.offpay.app.presentation.BalanceViewModel
import com.offpay.app.presentation.ui.components.NeoPopAccentCard
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopDangerOutlinedButton
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.components.PinBoxes
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bank balance check screen.
 *  - Page title at the top.
 *  - Idle state: small "BANK BALANCE" line + a centered inline PIN entry
 *    section (the hero), followed by a "CHECK NOW" NeoPOP button. Auto-fires
 *    once the user types 6 digits.
 *  - During a session: stepped progress card replaces the entry section.
 *  - Success: balance card with the prior result is the hero, PIN section
 *    folds away. "Check Again" resets the PIN field.
 *  - Persistence: last balance text + timestamp survive app kill, exposed
 *    via PreferencesRepository.
 */
@Composable
fun BalanceScreen(
    viewModel: BalanceViewModel,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val mode by viewModel.operationMode.collectAsState()
    val last by viewModel.lastResult.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()

    val pinFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-focus the PIN section as soon as the screen appears (idle, non-manual).
    // Deferred to after the first layout pass — requesting focus before the
    // parent chain has been placed crashes the Compose BringIntoView responder
    // when the IME tries to bring the focused field into view.
    LaunchedEffect(session, mode) {
        if (session is SessionState.Idle && mode != OperationMode.MANUAL) {
            withFrameNanos { /* one frame: layout placement committed */ }
            delay(50)
            runCatching { pinFocus.requestFocus() }
        }
    }
    // Auto-fire when the user has tapped in 6 digits.
    LaunchedEffect(ui.pin, mode) {
        if (ui.pin.length == 6 && mode != OperationMode.MANUAL && session is SessionState.Idle) {
            delay(250)
            keyboard?.hide()
            viewModel.attemptCheckBalance()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Balance",
                style = NeoPopType.DisplayLarge,
                color = NeoPopColors.TextPrimary
            )

            Spacer(Modifier.height(28.dp))

            Box(Modifier.weight(1f)) {
                when (val s = session) {
                    is SessionState.Idle -> IdleHero(
                        last = last,
                        mode = mode,
                        pin = ui.pin,
                        pinError = ui.pinError,
                        onTapPin = { runCatching { pinFocus.requestFocus() } }
                    )
                    is SessionState.Running -> SessionRunning(s)
                    is SessionState.Success -> SuccessHero(s.resultText)
                    is SessionState.Failed -> FailedCard(s.message, s.resultText) {
                        viewModel.dismissSession()
                    }
                }
            }

            when (val s = session) {
                is SessionState.Idle -> {
                    val ctaText = when (mode) {
                        OperationMode.MANUAL -> "Open Dialer"
                        else -> "Check Now"
                    }
                    NeoPopPrimaryButton(
                        text = ctaText,
                        onClick = {
                            keyboard?.hide()
                            viewModel.attemptCheckBalance()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is SessionState.Success -> {
                    NeoPopPrimaryButton(
                        text = "Check Again",
                        onClick = { viewModel.dismissSession() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is SessionState.Running -> {
                    NeoPopDangerOutlinedButton(
                        text = "Cancel",
                        onClick = viewModel::cancelSession,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> Unit
            }

            Spacer(Modifier.height(24.dp))
        }

        // Hidden PIN field rendered as a SIBLING of the main content Column,
        // not nested inside it. This keeps it in the layout tree (so it can
        // hold focus and surface the IME) while staying out of any
        // BringIntoView walk paths that fire from the IME — which is what
        // crashes Compose's BringIntoView responder when the parent chain
        // hasn't been placed yet on the first composition.
        //
        // 1.dp + alpha(0f) (vs the old size(0.dp)) gives the field a real,
        // fully-placed layout box, sidestepping the placement edge case.
        if (session is SessionState.Idle && mode != OperationMode.MANUAL) {
            val view = LocalView.current
            BasicTextField(
                value = ui.pin,
                onValueChange = { v ->
                    val before = ui.pin.length
                    viewModel.onPinChanged(v)
                    if (v.length > before) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .focusRequester(pinFocus)
                    .size(1.dp)
                    .alpha(0f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                cursorBrush = SolidColor(NeoPopColors.Black),
                singleLine = true,
                textStyle = TextStyle(color = NeoPopColors.Black)
            )
        }

        // Snackbar pinned to the bottom for 2.5s.
        SnackbarBanner(
            message = snackbar,
            onDismiss = viewModel::dismissSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }
}

@Composable
private fun SnackbarBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (message != null) {
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .background(NeoPopColors.SurfaceHigher)
                    .drawBehind {
                        drawRect(
                            color = NeoPopColors.Accent,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun IdleHero(
    last: BalanceResult?,
    mode: OperationMode,
    pin: String,
    pinError: String?,
    onTapPin: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(NeoPopColors.Accent)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "BANK BALANCE",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.TextSecondary
            )
        }

        if (last != null) {
            Spacer(Modifier.height(16.dp))
            val parsed = parseBalance(last.text)
            Text(
                text = parsed ?: last.text,
                style = NeoPopType.MonoLarge.copy(
                    color = NeoPopColors.TextPrimary,
                    fontSize = if (parsed != null) 36.sp else 22.sp
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "LAST CHECKED · ${formatTimestamp(last.timestamp)}",
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.TextMuted
            )
        }

        Spacer(Modifier.height(28.dp))

        if (mode == OperationMode.MANUAL) {
            // No PIN entry — manual mode hands off to the dialer.
            ManualHero()
        } else {
            BalancePinSection(
                pin = pin,
                onTap = onTapPin,
                error = pinError
            )
        }
    }
}

@Composable
private fun ManualHero() {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MANUAL MODE",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap below to open the dialer with *99*3# prefilled. Enter your PIN there.",
            style = NeoPopType.BodyMedium,
            color = NeoPopColors.TextSecondary
        )
    }
}

/**
 * Centered, lime-bordered PIN entry section. Same idea as the Pay screen
 * version but framed as the balance screen's primary control.
 *
 * The hidden BasicTextField backing this control lives OUTSIDE this
 * composable (rendered as a sibling at the bottom of [BalanceScreen]'s
 * outer Box). Tapping anywhere on the card forwards focus to that hidden
 * field via [onTap] — which surfaces the keyboard.
 */
@Composable
private fun BalancePinSection(
    pin: String,
    onTap: () -> Unit,
    error: String?
) {
    NeoPopAccentCard(
        accent = NeoPopColors.Accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ENTER UPI PIN",
                style = NeoPopType.LabelLarge,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(14.dp))
            PinBoxes(
                value = pin,
                length = 6,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.Danger
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "tap any box to bring up the keyboard",
                    style = NeoPopType.LabelSmall,
                    color = NeoPopColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun SessionRunning(state: SessionState.Running) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BALANCE CHECK IN PROGRESS",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(12.dp))
        NeoPopCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = state.label,
                    style = NeoPopType.HeadlineLarge,
                    color = NeoPopColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Step ${state.stepIndex + 1} of ${state.total}",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { ((state.stepIndex + 1f) / state.total).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = NeoPopColors.Accent,
                    trackColor = NeoPopColors.Border
                )
            }
        }
    }
}

@Composable
private fun SuccessHero(resultText: String) {
    val parsed = parseBalance(resultText)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(NeoPopColors.Success))
            Spacer(Modifier.size(8.dp))
            Text(
                text = "BANK BALANCE",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Success
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = parsed ?: "₹ ——",
            style = NeoPopType.MonoLarge.copy(
                color = NeoPopColors.TextPrimary,
                fontSize = 48.sp
            )
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = resultText,
            style = NeoPopType.Mono.copy(color = NeoPopColors.TextSecondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "LAST CHECKED · just now",
            style = NeoPopType.LabelSmall,
            color = NeoPopColors.TextMuted
        )
    }
}

@Composable
private fun FailedCard(message: String, resultText: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        NeoPopAccentCard(accent = NeoPopColors.Danger, modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(NeoPopColors.Danger),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = NeoPopColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "BALANCE CHECK FAILED",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Danger
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = NeoPopType.HeadlineLarge,
                    color = NeoPopColors.TextPrimary
                )
                if (resultText.isNotBlank() && resultText != message) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = resultText,
                        style = NeoPopType.BodyMedium,
                        color = NeoPopColors.TextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        NeoPopPrimaryButton(text = "Try Again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

private val BALANCE_REGEX =
    Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

/**
 * Best-effort balance amount extraction from the carrier's reply text.
 * Looks for the first "₹ X,XXX.XX" / "Rs 12345.67" / "INR 100" pattern.
 */
private fun parseBalance(text: String): String? {
    val match = BALANCE_REGEX.find(text) ?: return null
    val rawAmount = match.groupValues[1]
    return "₹ $rawAmount"
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - ts
    if (delta < 60_000) return "just now"
    if (delta < 3_600_000) return "${delta / 60_000}m ago"
    if (delta < 86_400_000) return "${delta / 3_600_000}h ago"
    val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    return sdf.format(Date(ts))
}
