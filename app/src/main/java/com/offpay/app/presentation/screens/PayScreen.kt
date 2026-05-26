package com.offpay.app.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.domain.FormField
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
import com.offpay.app.presentation.ui.components.StatusBanner
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

@Composable
fun PayScreen(
    viewModel: PayViewModel,
    onNavigateScan: () -> Unit,
    permissions: PermissionStatus,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsState()
    val session by viewModel.sessionState.collectAsState()

    Box(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        when (val s = session) {
            is SessionState.Idle -> PayForm(
                ui = ui,
                permissions = permissions,
                onPay = { viewModel.startPayment(ui.vpa, ui.amount, ui.note, ui.pin) },
                onScan = onNavigateScan,
                onVpa = { v -> viewModel.onFormFieldChanged(vpa = v) },
                onAmount = { v -> viewModel.onFormFieldChanged(amount = v) },
                onNote = { v -> viewModel.onFormFieldChanged(note = v) },
                onPin = { v -> viewModel.onFormFieldChanged(pin = v) }
            )
            is SessionState.Running -> SessionRunningCard(s, onCancel = viewModel::cancelSession)
            is SessionState.Success -> SessionSuccessCard(s, onDone = viewModel::dismissSession)
            is SessionState.Failed -> SessionFailedCard(s, onRetry = viewModel::dismissSession)
        }
    }
}

// ─── Form ──────────────────────────────────────────────────────────────────────

@Composable
private fun PayForm(
    ui: PayUiState,
    permissions: PermissionStatus,
    onPay: () -> Unit,
    onScan: () -> Unit,
    onVpa: (String) -> Unit,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onPin: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launchers = rememberPermissionLaunchers()
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──
        Text(
            text = "OFFPAY",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Pay",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.TextPrimary
        )

        Spacer(Modifier.height(20.dp))

        PermissionGate(permissions = permissions, launchers = launchers, context = context)

        Spacer(Modifier.height(20.dp))

        AmountInput(
            value = ui.amount,
            onChange = onAmount,
            focusRequester = amountFocus,
            error = ui.errors[FormField.AMOUNT]
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "TO",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.TextSecondary
        )
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(16.dp))

        NeoPopTextField(
            value = ui.pin,
            onValueChange = onPin,
            label = "UPI PIN",
            placeholder = "••••",
            leadingIcon = Icons.Default.Lock,
            error = ui.errors[FormField.PIN],
            keyboardType = KeyboardType.NumberPassword,
            visualTransformation = PasswordVisualTransformation('•'),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        NeoPopPrimaryButton(
            text = "Pay ₹${ui.amount.ifBlank { "0" }}",
            leadingIcon = Icons.Default.Lock,
            onClick = {
                keyboard?.hide()
                onPay()
            },
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
}

@Composable
private fun PermissionGate(
    permissions: PermissionStatus,
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
            text = "PAY ₹",
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
            text = "PAYMENT IN PROGRESS",
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
                    modifier = Modifier.fillMaxWidth().height(4.dp),
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
                    text = "PAYMENT COMPLETE",
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
private fun SessionFailedCard(state: SessionState.Failed, onRetry: () -> Unit) {
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
                    text = "PAYMENT FAILED",
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
            }
        }
        Spacer(Modifier.height(20.dp))
        NeoPopPrimaryButton(text = "Try Again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}
