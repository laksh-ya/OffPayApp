package com.offpay.app.presentation.screens

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.offpay.app.R
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.OperationMode
import com.offpay.app.presentation.HistoryViewModel
import com.offpay.app.presentation.permissions.PermissionStatus
import com.offpay.app.presentation.permissions.openAccessibilitySettings
import com.offpay.app.presentation.permissions.openOverlaySettings
import com.offpay.app.presentation.permissions.rememberPermissionLaunchers
import com.offpay.app.presentation.ui.components.FallingEmoji
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopDangerOutlinedButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefsRepo: PreferencesRepository,
    historyViewModel: HistoryViewModel,
    permissions: PermissionStatus,
    versionName: String,
    onClearAllData: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by prefsRepo.operationMode.collectAsState(initial = OperationMode.AUTO)
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
            text = "Settings",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.TextPrimary
        )

        Spacer(Modifier.height(28.dp))

        // ── Mode ──
        SectionHeader("Mode")
        Spacer(Modifier.height(12.dp))
        // Two modes only — Auto (default, full overlay) and Manual (dialer
        // fallback). The legacy ADVANCED enum value still exists so old
        // preferences don't crash, but it's not user-selectable any more
        // and is treated identically to AUTO across the runtime.
        ModeOption(
            label = "Auto",
            description = "Default. OffPay handles the carrier dialog automatically — you stay in OffPay throughout.",
            selected = mode != OperationMode.MANUAL,
            onClick = { scope.launch { prefsRepo.setOperationMode(OperationMode.AUTO) } }
        )
        Spacer(Modifier.height(8.dp))
        ModeOption(
            label = "Manual",
            description = "Copies the UPI ID and opens the dialer. You enter the rest yourself. No accessibility needed.",
            selected = mode == OperationMode.MANUAL,
            onClick = { scope.launch { prefsRepo.setOperationMode(OperationMode.MANUAL) } }
        )

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))

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
        Hairline()
        Spacer(Modifier.height(24.dp))

        // ── Help & history shortcuts ──
        SectionHeader("More")
        Spacer(Modifier.height(12.dp))
        ShortcutRow(
            icon = Icons.Default.History,
            title = "Transaction History",
            subtitle = "Past payments",
            onClick = onOpenHistory
        )
        Spacer(Modifier.height(8.dp))
        ShortcutRow(
            icon = Icons.Default.Help,
            title = "Help & FAQ",
            subtitle = "How OffPay works, what to do when it fails",
            onClick = onOpenFaq
        )

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))

        // ── Share / PWA ──
        SectionHeader("Share")
        Spacer(Modifier.height(12.dp))
        ShortcutRow(
            icon = Icons.Default.Share,
            title = "Share OffPay",
            subtitle = "Send the installed APK (or install link)",
            onClick = { com.offpay.app.platform.ApkShareUtil.shareInstalledApk(context) }
        )
        Spacer(Modifier.height(8.dp))
        ShortcutRow(
            icon = Icons.Default.Language,
            title = "OffPay Web (PWA)",
            subtitle = "Manual mode in any browser — useful on iPhone",
            onClick = { openUrl(context, "https://offpay.vercel.app/") }
        )

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))

        // ── Danger ──
        // Sits under "Share" / above "About" so Clear-All-Data is a real
        // settings action, not an awkward neighbour to the credits line.
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

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))

        // ── About — last on the page; ends with the aesthetic-cat footer.
        AboutSection(
            versionName = versionName,
            onOpenLakshya = { openUrl(context, "https://github.com/laksh-ya/") },
            onOpenHarsh = { openUrl(context, "https://github.com/harshtripathi272/") }
        )

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Helper for opening external URLs from Settings rows.
 */
private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * About section — minimal credit "made by Lakshya & Harsh", an OffPay
 * logo + version line, and two hidden easter eggs:
 *   - Tap the **OffPay header row** (logo + name) once → summons the
 *     "big cat rises from the bottom" overlay. This is the about-OffPay
 *     egg, separate from the wordmark money-rain on the Pay screen.
 *   - Tap the **credit line** once → opens an action sheet with the
 *     two GitHub profiles. No counter, no easter egg here — just a
 *     clean tap-to-link.
 */
@Composable
private fun AboutSection(
    versionName: String,
    onOpenLakshya: () -> Unit,
    onOpenHarsh: () -> Unit
) {
    val view = LocalView.current
    var showCat by remember { mutableStateOf(false) }
    var showCreditSheet by remember { mutableStateOf(false) }

    Column {
        SectionHeader("About")
        Spacer(Modifier.height(12.dp))
        // Tappable header row → cat easter egg.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    showCat = true
                }
                .padding(vertical = 4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.offpay_logo),
                contentDescription = "OffPay",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "OffPay",
                    style = NeoPopType.TitleLarge,
                    color = NeoPopColors.TextPrimary
                )
                Text(
                    text = "Version $versionName",
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextMuted
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Credit line — single tap opens the GitHub action sheet. No
        // easter egg here; the cat lives on the OffPay header row above.
        Box(
            Modifier
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    showCreditSheet = true
                }
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "made by ",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextSecondary
                )
                Text(
                    text = "Lakshya & Harsh",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextPrimary
                )
            }
        }
    }

    // GitHub action sheet — only opens on a single tap.
    if (showCreditSheet) {
        CreditSheet(
            onDismiss = { showCreditSheet = false },
            onOpenLakshya = {
                showCreditSheet = false
                onOpenLakshya()
            },
            onOpenHarsh = {
                showCreditSheet = false
                onOpenHarsh()
            }
        )
    }

    // Big-cat-rising-from-bottom easter egg overlay.
    AnimatedVisibility(visible = showCat, enter = fadeIn(), exit = fadeOut()) {
        com.offpay.app.presentation.ui.components.CatLickingOverlay(
            onDone = { showCat = false }
        )
    }

    // Aesthetic-cat footer image. Sits at the bottom of the About card
    // as a wide banner — purely decorative, signals "end of content".
    // Resource is resolved reflectively so the build doesn't break if
    // artifacts/cat_aesthetic.png hasn't been packed yet via
    // scripts/pack_assets.py.
    val aestheticCatRes = remember {
        runCatching {
            R.drawable::class.java.getField("cat_aesthetic").getInt(null)
        }.getOrNull() ?: 0
    }
    if (aestheticCatRes != 0) {
        Spacer(Modifier.height(20.dp))
        Image(
            painter = painterResource(id = aestheticCatRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CreditSheet(
    onDismiss: () -> Unit,
    onOpenLakshya: () -> Unit,
    onOpenHarsh: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeoPopColors.SurfaceHigh
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "made by",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(12.dp))
            CreditRow(name = "Lakshya", handle = "@laksh-ya", onClick = onOpenLakshya)
            Spacer(Modifier.height(8.dp))
            CreditRow(name = "Harsh", handle = "@harshtripathi272", onClick = onOpenHarsh)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CreditRow(name: String, handle: String, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(NeoPopColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = NeoPopColors.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = NeoPopType.TitleMedium, color = NeoPopColors.TextPrimary)
            Text(handle, style = NeoPopType.BodySmall, color = NeoPopColors.TextMuted)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = NeoPopColors.TextMuted
        )
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
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NeoPopColors.Border)
    )
}

@Composable
private fun ModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) NeoPopColors.Accent else NeoPopColors.Border,
        label = "mode_border"
    )

    NeoPopCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!selected) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            },
        borderColor = borderColor
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(
                        if (selected) NeoPopColors.Accent else NeoPopColors.Surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(NeoPopColors.Black)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = NeoPopType.TitleLarge,
                    color = NeoPopColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextMuted
                )
            }
        }
    }
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
        val view = LocalView.current
        Box(
            Modifier
                .background(NeoPopColors.Accent)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
private fun ShortcutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    NeoPopCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
    ) {
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
                    tint = NeoPopColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = NeoPopType.TitleMedium,
                    color = NeoPopColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = NeoPopColors.TextMuted
            )
        }
    }
}
