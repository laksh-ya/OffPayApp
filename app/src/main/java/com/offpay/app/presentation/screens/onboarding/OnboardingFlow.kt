package com.offpay.app.presentation.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.permissions.PermissionStatus
import com.offpay.app.presentation.permissions.openAccessibilitySettings
import com.offpay.app.presentation.permissions.openOverlaySettings
import com.offpay.app.presentation.permissions.rememberPermissionLaunchers
import com.offpay.app.presentation.permissions.rememberPermissionStatus
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.launch

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val permissionsState = rememberPermissionStatus()
    val permissions = permissionsState.value

    Column(
        Modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> Star99ExplainerPage()
                2 -> PermissionsPage(permissions = permissions)
                3 -> ReadyPage()
            }
        }
        Spacer(Modifier.height(8.dp))
        PageIndicator(current = pagerState.currentPage, total = 4)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.padding(20.dp)) {
            val isLast = pagerState.currentPage == 3
            NeoPopPrimaryButton(
                text = when (pagerState.currentPage) {
                    0 -> "Continue"
                    1 -> "Got It"
                    2 -> if (permissions.readyForOverlayPay) "Looks Good" else "Next"
                    else -> "Let's Pay"
                },
                onClick = {
                    if (isLast) {
                        onComplete()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PageIndicator(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 24.dp else 8.dp, height = 4.dp)
                    .background(if (i == current) NeoPopColors.Accent else NeoPopColors.Border)
            )
            if (i < total - 1) Spacer(Modifier.width(4.dp))
        }
    }
}

// ── Page 1: Welcome ──

@Composable
private fun WelcomePage() {
    val transition = rememberInfiniteTransition(label = "welcome")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .alpha(pulse)
                    .background(NeoPopColors.Accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "OFFLINE UPI",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "OffPay",
            style = NeoPopType.DisplayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = NeoPopColors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pay over UPI without internet. Just *99# and a phone signal.",
            style = NeoPopType.BodyLarge,
            color = NeoPopColors.TextSecondary
        )
    }
}

// ── Page 2: What is *99# ──

@Composable
private fun Star99ExplainerPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WHY *99#",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "USSD makes UPI work without data.",
            style = NeoPopType.DisplayMedium,
            color = NeoPopColors.TextPrimary
        )
        Spacer(Modifier.height(20.dp))
        NeoPopCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "*99# is NPCI's USSD shortcode for offline UPI. It rides over your phone's signaling channel — the same one that carries calls and SMS — so it works in low-coverage areas where mobile data fails.",
                    style = NeoPopType.BodyLarge,
                    color = NeoPopColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CarrierPill("AIRTEL", supported = true, modifier = Modifier.weight(1f))
            CarrierPill("VI", supported = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CarrierPill("BSNL", supported = true, modifier = Modifier.weight(1f))
            CarrierPill("JIO", supported = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CarrierPill(name: String, supported: Boolean, modifier: Modifier = Modifier) {
    val accent = if (supported) NeoPopColors.Success else NeoPopColors.Danger
    Box(
        modifier
            .background(NeoPopColors.SurfaceHigh)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.TextPrimary
            )
        }
    }
}

// ── Page 3: Permissions ──

@Composable
private fun PermissionsPage(permissions: PermissionStatus) {
    val context = LocalContext.current
    val launchers = rememberPermissionLaunchers()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "PERMISSIONS",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "OffPay needs four things.",
            style = NeoPopType.DisplayMedium,
            color = NeoPopColors.TextPrimary
        )
        Spacer(Modifier.height(20.dp))
        PermissionTile(
            icon = Icons.Default.Phone,
            title = "Phone",
            why = "Dial *99# and read the active SIM's carrier.",
            granted = permissions.phoneBundle,
            onGrant = { launchers.requestPhoneBundle() }
        )
        Spacer(Modifier.height(12.dp))
        PermissionTile(
            icon = Icons.Default.Camera,
            title = "Camera",
            why = "Scan UPI QR codes to autofill the recipient.",
            granted = permissions.camera,
            onGrant = { launchers.requestCamera() }
        )
        Spacer(Modifier.height(12.dp))
        PermissionTile(
            icon = Icons.Default.Settings,
            title = "Accessibility",
            why = "Read and reply to the carrier's USSD dialog.",
            granted = permissions.accessibility,
            onGrant = { openAccessibilitySettings(context) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionTile(
            icon = Icons.Default.Layers,
            title = "Display Over Other Apps",
            why = "Cover the system dialog with OffPay's overlay.",
            granted = permissions.overlay,
            onGrant = { openOverlaySettings(context) }
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionTile(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(NeoPopColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) NeoPopColors.Accent else NeoPopColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = why,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextMuted
                )
            }
            Spacer(Modifier.width(10.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = NeoPopColors.Success,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                val view = androidx.compose.ui.platform.LocalView.current
                Box(
                    Modifier
                        .background(NeoPopColors.Accent)
                        .clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            onGrant()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "GRANT",
                        style = NeoPopType.LabelSmall,
                        color = NeoPopColors.Black
                    )
                }
            }
        }
    }
}

// ── Page 4: Ready ──

@Composable
private fun ReadyPage() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(NeoPopColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = NeoPopColors.Black,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "YOU'RE SET",
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Time to pay.",
            style = NeoPopType.DisplayLarge,
            color = NeoPopColors.TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}
