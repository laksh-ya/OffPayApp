package com.offpay.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.OperationMode
import com.offpay.app.presentation.HistoryViewModel
import com.offpay.app.presentation.permissions.PermissionStatus
import com.offpay.app.presentation.permissions.openAccessibilitySettings
import com.offpay.app.presentation.permissions.openOverlaySettings
import com.offpay.app.presentation.permissions.rememberPermissionLaunchers
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopDangerOutlinedButton
import com.offpay.app.presentation.ui.components.NeoPopToggle
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.launch

/**
 * Two simplified modes shown in settings: Dialer (manual fallback that
 * opens the system dialer with the code prefilled) and Auto (overlay mode
 * with full automation). The underlying [OperationMode.ADVANCED] is collapsed
 * into Auto along with [OperationMode.OVERLAY].
 */
private enum class UiMode { Dialer, Auto }

private fun OperationMode.toUiMode(): UiMode =
    if (this == OperationMode.DIALER) UiMode.Dialer else UiMode.Auto

private fun UiMode.toOperationMode(): OperationMode =
    if (this == UiMode.Dialer) OperationMode.DIALER else OperationMode.OVERLAY

@Composable
fun SettingsScreen(
    prefsRepo: PreferencesRepository,
    historyViewModel: HistoryViewModel,
    permissions: PermissionStatus,
    versionName: String,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by prefsRepo.operationMode.collectAsState(initial = OperationMode.OVERLAY)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val launchers = rememberPermissionLaunchers()

    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "OFFPAY",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Settings",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.TextPrimary
        )

        Spacer(Modifier.height(28.dp))

        // ── Mode ──
        SectionHeader("Mode")
        Spacer(Modifier.height(12.dp))
        NeoPopToggle(
            options = listOf(
                UiMode.Dialer to "Dialer",
                UiMode.Auto to "Auto"
            ),
            selected = mode.toUiMode(),
            onSelect = { ui ->
                scope.launch { prefsRepo.setOperationMode(ui.toOperationMode()) }
            }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (mode == OperationMode.DIALER) {
                "Dialer mode opens your phone's USSD dialog. You'll type the steps manually."
            } else {
                "Auto mode hides the carrier dialog with OffPay's overlay and drives the session for you."
            },
            style = NeoPopType.BodySmall,
            color = NeoPopColors.TextMuted
        )

        Spacer(Modifier.height(28.dp))

        // ── Permissions ──
        SectionHeader("Permissions")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionRow(
                icon = Icons.Default.Phone,
                label = "Phone access",
                description = "Dial *99# and read SIM info",
                granted = permissions.phoneBundle,
                onFix = { launchers.requestPhoneBundle() }
            )
            PermissionRow(
                icon = Icons.Default.Camera,
                label = "Camera",
                description = "Scan UPI QR codes",
                granted = permissions.camera,
                onFix = { launchers.requestCamera() }
            )
            PermissionRow(
                icon = Icons.Default.Settings,
                label = "Accessibility",
                description = "Read and reply to the carrier dialog",
                granted = permissions.accessibility,
                onFix = { openAccessibilitySettings(context) }
            )
            PermissionRow(
                icon = Icons.Default.Layers,
                label = "Display over other apps",
                description = "Cover the system USSD dialog",
                granted = permissions.overlay,
                onFix = { openOverlaySettings(context) }
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── FAQ ──
        SectionHeader("FAQ")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FaqItem(
                question = "What is *99#?",
                answer = "*99# is NPCI's USSD code for offline UPI payments. It works without internet over your phone's signaling channel, so you can pay or check balance even when you have no data."
            )
            FaqItem(
                question = "Which carriers work?",
                answer = "Airtel, Vi, and BSNL support *99# reliably. Jio's coverage is unreliable, so OffPay warns you before dialing on Jio SIMs."
            )
            FaqItem(
                question = "How does the overlay work?",
                answer = "Auto mode opens the system USSD dialog and immediately covers it with OffPay's overlay. Behind the scenes, an accessibility service reads the dialog text and types in the right replies for each step."
            )
            FaqItem(
                question = "Is my UPI PIN safe?",
                answer = "Your PIN never leaves the phone. It's held only in memory while a session is active and cleared within 500ms of completion or cancellation. It's never logged or persisted."
            )
            FaqItem(
                question = "What if it fails?",
                answer = "OffPay surfaces the carrier's exact error wording. Common causes: wrong PIN, invalid UPI ID, insufficient balance, or the recipient's handle isn't registered. Check the message and try again."
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── About ──
        SectionHeader("About")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Made by Lakshya & Harsh",
            style = NeoPopType.BodyMedium,
            color = NeoPopColors.TextSecondary
        )
        Text(
            text = "Version $versionName",
            style = NeoPopType.BodySmall,
            color = NeoPopColors.TextMuted
        )

        Spacer(Modifier.height(28.dp))

        // ── Danger ──
        SectionHeader("Danger", danger = true)
        Spacer(Modifier.height(12.dp))
        NeoPopDangerOutlinedButton(
            text = "Clear All Data",
            onClick = {
                historyViewModel.clearHistory()
                onClearAllData()
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(label: String, danger: Boolean = false) {
    Text(
        text = label.uppercase(),
        style = NeoPopType.LabelMedium,
        color = if (danger) NeoPopColors.Danger else NeoPopColors.Accent
    )
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(NeoPopColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) NeoPopColors.Accent else NeoPopColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = NeoPopType.TitleMedium,
                    color = NeoPopColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextMuted
                )
            }
            Spacer(Modifier.size(10.dp))
            StatusPill(granted = granted, onFix = onFix)
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean, onFix: () -> Unit) {
    if (granted) {
        Box(
            Modifier
                .background(NeoPopColors.Success.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "GRANTED",
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.Success
            )
        }
    } else {
        val view = androidx.compose.ui.platform.LocalView.current
        Box(
            Modifier
                .background(NeoPopColors.Accent)
                .clickable {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onFix()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "FIX",
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.Black
            )
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    NeoPopCard(modifier = Modifier
        .fillMaxWidth()
        .clickable { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question,
                    style = NeoPopType.TitleMedium,
                    color = NeoPopColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (open) Icons.Default.Add else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NeoPopColors.Accent,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (open) 45f else 0f)
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(NeoPopColors.Border)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = answer,
                        style = NeoPopType.BodyMedium,
                        color = NeoPopColors.TextSecondary
                    )
                }
            }
        }
    }
}
