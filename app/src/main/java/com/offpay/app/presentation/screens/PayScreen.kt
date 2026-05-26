package com.offpay.app.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.domain.FormField
import com.offpay.app.domain.OperationMode
import com.offpay.app.domain.SessionState
import com.offpay.app.presentation.PayUiState
import com.offpay.app.presentation.PayViewModel
import com.offpay.app.presentation.permissions.PermissionStatus
import com.offpay.app.presentation.permissions.openAccessibilitySettings
import com.offpay.app.presentation.permissions.openOverlaySettings
import com.offpay.app.presentation.permissions.rememberPermissionLaunchers
import com.offpay.app.presentation.ui.components.NeoPopAccentCard
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopDangerOutlinedButton
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.components.NeoPopSecondaryButton
import com.offpay.app.presentation.ui.components.NeoPopTextField
import com.offpay.app.presentation.ui.components.PinBoxes
import com.offpay.app.presentation.ui.components.StatusBanner
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos

@Composable
fun PayScreen(
    viewModel: PayViewModel,
    onNavigateScan: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateFaq: () -> Unit,
    permissions: PermissionStatus,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val mode by viewModel.operationMode.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()

    // ── OffPay-wordmark easter egg ────────────────────────────────────
    // Five quick taps on the wordmark trigger a falling-money rain across
    // the screen. The counter resets if the user pauses for >1.4s.
    var wordmarkTaps by remember { mutableStateOf(0) }
    var lastWordmarkTap by remember { mutableStateOf(0L) }
    var showMoneyRain by remember { mutableStateOf(false) }
    LaunchedEffect(wordmarkTaps) {
        if (wordmarkTaps in 1..4) {
            delay(1_500)
            if (System.currentTimeMillis() - lastWordmarkTap > 1_400) {
                wordmarkTaps = 0
            }
        }
    }
    val onWordmarkTap: () -> Unit = {
        val now = System.currentTimeMillis()
        wordmarkTaps = if (now - lastWordmarkTap < 1_400) wordmarkTaps + 1 else 1
        lastWordmarkTap = now
        if (wordmarkTaps >= 5) {
            showMoneyRain = true
            wordmarkTaps = 0
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        when (val s = session) {
            is SessionState.Idle -> PayForm(
                ui = ui,
                mode = mode,
                permissions = permissions,
                onPay = { viewModel.attemptPayment() },
                onScan = onNavigateScan,
                onHistory = onNavigateHistory,
                onVpa = { v -> viewModel.onFormFieldChanged(vpa = v) },
                onAmount = { v -> viewModel.onFormFieldChanged(amount = v) },
                onNote = { v -> viewModel.onFormFieldChanged(note = v) },
                onPinChanged = viewModel::onPinChanged,
                onWordmarkTap = onWordmarkTap
            )
            is SessionState.Running -> SessionRunningCard(s, onCancel = viewModel::cancelSession)
            is SessionState.Success -> SessionSuccessCard(s, onDone = viewModel::dismissSession)
            is SessionState.Failed -> SessionFailedCard(
                state = s,
                onRetry = viewModel::dismissSession,
                onWhyFailed = onNavigateFaq
            )
        }

        // Toast-style snackbar pinned to the bottom for 2.5s.
        SnackbarBanner(
            message = snackbar,
            onDismiss = viewModel::dismissSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Money-rain easter egg layer.
        AnimatedVisibility(
            visible = showMoneyRain,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MoneyRainOverlay(onDone = { showMoneyRain = false })
        }
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

// ─── Form ──────────────────────────────────────────────────────────────────────

@Composable
private fun PayForm(
    ui: PayUiState,
    mode: OperationMode,
    permissions: PermissionStatus,
    onPay: () -> Unit,
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onVpa: (String) -> Unit,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onWordmarkTap: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launchers = rememberPermissionLaunchers()
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }

    // Defer the initial focus request until after the first layout pass
    // completes — requesting focus during composition (or before parent
    // placement) crashes the BringIntoView responder on Compose-foundation.
    LaunchedEffect(Unit) {
        withFrameNanos { /* one frame: layout placement is now committed */ }
        delay(50)
        runCatching { amountFocus.requestFocus() }
    }

    // The PIN section only appears AFTER the user taps the primary Pay
    // button (and the form's VPA + amount validate cleanly). Auto-popping
    // it while the user was still typing the UPI ID was confusing — the
    // section would flicker in and out as they fixed typos.
    var showPinSection by remember { mutableStateOf(false) }

    // If the user edits the form back into an invalid state, hide the
    // section again so the next "Pay" tap re-validates cleanly.
    val vpaLooksValid = ui.vpa.contains('@') && ui.vpa.substringAfter('@').isNotEmpty()
    val amountLooksValid = ui.amount.toDoubleOrNull()?.let { it > 0.0 } == true
    LaunchedEffect(vpaLooksValid, amountLooksValid, mode) {
        if (!vpaLooksValid || !amountLooksValid || mode == OperationMode.MANUAL) {
            showPinSection = false
        }
    }

    val pinSectionVisible = showPinSection && mode != OperationMode.MANUAL

    LaunchedEffect(pinSectionVisible) {
        if (pinSectionVisible && ui.pin.isEmpty()) {
            // Same deferred pattern as above so the section's parents have
            // been measured/placed before the hidden field grabs focus.
            withFrameNanos { /* wait one frame */ }
            delay(50)
            runCatching { pinFocus.requestFocus() }
            keyboard?.show()
        }
    }

    // Auto-fire when 6 digits entered and form is valid (debounced 250ms).
    LaunchedEffect(ui.pin) {
        if (ui.pin.length == 6 && pinSectionVisible) {
            delay(250)
            keyboard?.hide()
            onPay()
        }
    }

    // Click handler for the primary Pay button. In MANUAL mode it falls
    // straight through to the ViewModel (clipboard + dialer fallback).
    // Otherwise: validate first, and if everything checks out, reveal the
    // inline PIN section. If the PIN's already filled in, fire off the
    // payment immediately.
    val onPayClicked: () -> Unit = handler@{
        keyboard?.hide()
        if (mode == OperationMode.MANUAL) {
            onPay()
            return@handler
        }
        if (!vpaLooksValid || !amountLooksValid) {
            // Surface the field-level errors via the ViewModel's normal path.
            onPay()
            return@handler
        }
        if (!showPinSection) {
            showPinSection = true
            return@handler
        }
        onPay()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Header: wordmark + history shortcut ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                OffPayWordmark(
                    modifier = Modifier.weight(1f),
                    onClick = onWordmarkTap
                )
                HistoryShortcut(onClick = onHistory)
            }

            Spacer(Modifier.height(24.dp))

            PermissionGate(
                permissions = permissions,
                mode = mode,
                launchers = launchers,
                context = context
            )

            Spacer(Modifier.height(24.dp))

            AmountInput(
                value = ui.amount,
                onChange = onAmount,
                focusRequester = amountFocus,
                error = ui.errors[FormField.AMOUNT]
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "to",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            NeoPopTextField(
                value = ui.vpa,
                onValueChange = onVpa,
                label = "UPI ID",
                placeholder = "username@bank",
                error = ui.errors[FormField.VPA],
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            NeoPopTextField(
                value = ui.note,
                onValueChange = onNote,
                label = "Note (optional)",
                placeholder = "Coffee",
                modifier = Modifier.fillMaxWidth()
            )

            // ── Inline PIN section (Advanced/Auto only) ──
            if (mode != OperationMode.MANUAL) {
                Spacer(Modifier.height(20.dp))
                AnimatedVisibility(
                    visible = pinSectionVisible,
                    enter = fadeIn() + slideInVertically { it / 4 },
                    exit = fadeOut() + slideOutVertically { it / 4 }
                ) {
                    InlinePinSection(
                        value = ui.pin,
                        onTap = {
                            runCatching { pinFocus.requestFocus() }
                            keyboard?.show()
                        },
                        error = ui.errors[FormField.PIN]
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            val payButtonText = when {
                mode == OperationMode.MANUAL -> "Open Dialer"
                !showPinSection -> "Continue"
                else -> "Pay ₹${ui.amount.ifBlank { "0" }}"
            }
            NeoPopPrimaryButton(
                text = payButtonText,
                leadingIcon = Icons.Default.Lock,
                onClick = onPayClicked,
                enabled = permissions.readyForDialerPay,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            NeoPopSecondaryButton(
                text = "Scan QR",
                leadingIcon = Icons.Default.QrCodeScanner,
                onClick = onScan,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(40.dp))
        }

        // Hidden PIN BasicTextField rendered as a SIBLING of the scrolling
        // Column, not inside it. This keeps it in the layout tree so it can
        // receive focus and surface the IME, but takes it out of the scroll
        // container's BringIntoView walk path — which is what was crashing
        // when the IME tried to scroll the focused field into view before
        // the scroll Column had been placed.
        //
        // 1.dp + alpha(0f) (vs the old Modifier.size(0.dp)) gives the field
        // a real, fully-placed layout box, sidestepping the placement edge
        // case that triggered ContentInViewNode.calculateRectForParent's
        // `Expected BringIntoViewRequester to not be used before parents
        // are placed` IllegalStateException.
        if (mode != OperationMode.MANUAL) {
            val view = LocalView.current
            BasicTextField(
                value = ui.pin,
                onValueChange = { v ->
                    val before = ui.pin.length
                    onPinChanged(v)
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
    }
}

/**
 * Highlighted "ENTER UPI PIN" section. Wrapped in a [NeoPopAccentCard]
 * with a thin lime border so it visually pops on the form.
 *
 * The hidden BasicTextField that backs these boxes lives OUTSIDE the
 * scrolling Column (rendered as a sibling in [PayForm]'s outer Box). That
 * separation is intentional: keeping the focus-grabbing field out of the
 * verticalScroll's parent chain avoids the BringIntoView placement crash
 * that fires when the IME asks the scroll container to bring the focused
 * field into view before the parents have been placed.
 *
 * Tapping anywhere on the card forwards focus to that hidden field via
 * [onTap], which is what brings up the keyboard.
 */
@Composable
private fun InlinePinSection(
    value: String,
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
                text = "Enter UPI PIN",
                style = NeoPopType.LabelLarge,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(14.dp))
            PinBoxes(
                value = value,
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
private fun HistoryShortcut(onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "history_scale"
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(NeoPopColors.SurfaceHigh)
            .drawBehind {
                drawCircle(
                    color = NeoPopColors.TextPrimary.copy(alpha = 0.10f),
                    radius = size.minDimension / 2f - 0.5f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
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
            imageVector = Icons.Default.History,
            contentDescription = "History",
            tint = NeoPopColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * "OffPay" wordmark — "Off" in lime, "Pay" in white. Used as the title on
 * the Pay screen header (and reusable from anywhere else that needs the
 * brand mark instead of a literal page title).
 *
 * Tappable: passing a non-null [onClick] makes the wordmark react to taps
 * (with haptic + scale feedback). Used on the Pay screen for the money-
 * drop easter egg that fires after 5 quick taps.
 */
@Composable
fun OffPayWordmark(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "wordmark_scale"
    )
    val tap = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    } else Modifier

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(tap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Off",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.Accent
        )
        Text(
            text = "Pay",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.TextPrimary
        )
    }
}

@Composable
private fun PermissionGate(
    permissions: PermissionStatus,
    mode: OperationMode,
    launchers: com.offpay.app.presentation.permissions.PermissionLaunchers,
    context: android.content.Context
) {
    if (!permissions.phoneBundle) {
        StatusBanner(
            title = "Phone access needed",
            message = "OffPay needs phone access to dial *99# for offline payments.",
            actionLabel = "Fix",
            onAction = { launchers.requestPhoneBundle() }
        )
        Spacer(Modifier.height(8.dp))
    }
    // Manual mode doesn't use accessibility or the overlay — don't nag.
    if (mode != OperationMode.MANUAL) {
        if (!permissions.accessibility) {
            StatusBanner(
                title = "Accessibility off",
                message = "Enable OffPay's accessibility service so we can drive the carrier dialog.",
                actionLabel = "Fix",
                onAction = { openAccessibilitySettings(context) }
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!permissions.overlay) {
            StatusBanner(
                title = "Overlay off",
                message = "Allow display over other apps to hide the system USSD dialog.",
                actionLabel = "Fix",
                onAction = { openOverlaySettings(context) }
            )
        }
    }
}

/**
 * Big centered amount input. Decimal-only, max 2 dp, max 7 integer digits
 * (the *99# RBI cap is ₹5000 — but we let the user type more so the
 * validator can give a precise error).
 */
@Composable
private fun AmountInput(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    error: String?
) {
    val borderColor = if (error != null) NeoPopColors.Danger else NeoPopColors.Accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "you pay",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "₹",
                style = NeoPopType.MonoLarge.copy(
                    color = NeoPopColors.TextSecondary,
                    fontSize = 40.sp
                )
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    if (v.length <= 8 && v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
                        onChange(v)
                    }
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .padding(horizontal = 8.dp),
                textStyle = NeoPopType.MonoLarge.copy(
                    color = NeoPopColors.TextPrimary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(NeoPopColors.Accent),
                singleLine = true,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "0",
                            style = NeoPopType.MonoLarge.copy(
                                color = NeoPopColors.TextMuted,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                    inner()
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .height(2.dp)
                .fillMaxWidth(0.6f)
                .background(borderColor)
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error, style = NeoPopType.BodySmall, color = NeoPopColors.Danger)
        }
    }
}

// ─── Session states ────────────────────────────────────────────────────────────

@Composable
private fun SessionRunningCard(state: SessionState.Running, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Payment in progress",
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
        Spacer(Modifier.height(20.dp))
        NeoPopDangerOutlinedButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SessionSuccessCard(state: SessionState.Success, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        NeoPopAccentCard(accent = NeoPopColors.Success, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.Start) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(NeoPopColors.Success),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = NeoPopColors.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Payment complete",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Success
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.resultText,
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextPrimary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        NeoPopPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SessionFailedCard(
    state: SessionState.Failed,
    onRetry: () -> Unit,
    onWhyFailed: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        NeoPopAccentCard(accent = NeoPopColors.Danger, modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(NeoPopColors.Danger),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = NeoPopColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Payment failed",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Danger
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = NeoPopType.HeadlineLarge,
                    color = NeoPopColors.TextPrimary
                )
                if (state.resultText.isNotBlank() && state.resultText != state.message) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.resultText,
                        style = NeoPopType.BodyMedium,
                        color = NeoPopColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Why did this fail?",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Accent,
                    modifier = Modifier.clickable { onWhyFailed() }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        NeoPopPrimaryButton(text = "Try Again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Money-rain easter egg. Drops 💸/💰/💵 emojis from the top of the screen
 * for ~2.8 seconds. Triggered by tapping the OffPay wordmark 5+ times in
 * quick succession. The "ch-chingg" SFX is approximated with a CONFIRM
 * haptic — no audio asset is bundled.
 */
@Composable
private fun MoneyRainOverlay(onDone: () -> Unit) {
    val view = LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    data class Drop(val emoji: String, val xFraction: Float, val delayMs: Long, val durationMs: Int, val size: Int)
    val drops = remember {
        val pool = listOf("💸", "💰", "💵", "🪙")
        List(28) {
            Drop(
                emoji = pool[kotlin.random.Random.nextInt(pool.size)],
                xFraction = kotlin.random.Random.nextFloat(),
                delayMs = kotlin.random.Random.nextLong(0, 900),
                durationMs = kotlin.random.Random.nextInt(1_300, 2_300),
                size = kotlin.random.Random.nextInt(30, 56)
            )
        }
    }

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        delay(2_800)
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        drops.forEach { d ->
            com.offpay.app.presentation.ui.components.FallingEmoji(
                emoji = d.emoji,
                xPx = d.xFraction * screenWidthPx,
                fallToPx = screenHeightPx + 64f,
                durationMs = d.durationMs,
                delayMs = d.delayMs,
                sizeSp = d.size
            )
        }
    }
}
