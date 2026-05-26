package com.offpay.app.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.offpay.app.platform.QrScannerManager
import com.offpay.app.presentation.permissions.openAppDetailsSettings
import com.offpay.app.presentation.ui.components.NeoPopAccentCard
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.components.NeoPopSecondaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iPhone-native, Paytm-style QR scanner.
 *
 * Visual identity:
 *  - Square viewfinder centered, 70% of min(W, H).
 *  - 60% black scrim outside the viewfinder (cutout via even-odd path).
 *  - Lime L-corner brackets that pulse subtly on idle, snap inward + turn
 *    white on detection.
 *  - Sweeping lime scan line with a soft glow trailing it.
 *  - Glassy circular buttons (torch / gallery / help) at the bottom.
 *  - Top: floating back chip (left) and zoom chip (right, tap to cycle 1×/2×/3×).
 *  - Pinch-to-zoom + tap-to-focus animations.
 */
@Composable
fun ScanScreen(
    qrManager: QrScannerManager,
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    onOpenFaq: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
    ) {
        when {
            hasCameraPermission -> CameraScannerContent(
                qrManager = qrManager,
                onResult = onResult,
                onClose = onClose,
                onOpenFaq = onOpenFaq
            )
            else -> PermissionDeniedContent(
                permanentlyDenied = permissionDenied,
                onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppDetailsSettings(context) },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun CameraScannerContent(
    qrManager: QrScannerManager,
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    onOpenFaq: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(false) }
    var focusTap by remember { mutableStateOf<Offset?>(null) }
    var lastPinchAt by remember { mutableStateOf(0L) }
    var showZoomSlider by remember { mutableStateOf(false) }

    // Hide zoom slider 1s after pinch input stops
    LaunchedEffect(lastPinchAt) {
        if (lastPinchAt > 0) {
            showZoomSlider = true
            delay(1000)
            if (System.currentTimeMillis() - lastPinchAt >= 950) {
                showZoomSlider = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                qrManager.decodeFromUri(context, uri)?.let { raw ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    detected = true
                    delay(180)
                    onResult(raw)
                }
            }
        }
    }

    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        qrManager.bindToLifecycle(lifecycleOwner, previewView) { raw ->
            if (!detected) {
                detected = true
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                // Brief 180ms hold so the lime corners get to snap into
                // place + the success flash registers, then fade through
                // and hand off. The dwell used to be 700ms but the user
                // reported it felt buggy/slow; the visual "captured" cue
                // is carried by the fade-out overlay (DetectionFlash).
                scope.launch {
                    delay(180)
                    onResult(raw)
                }
            }
        }
        onDispose { qrManager.unbind() }
    }

    Box(Modifier.fillMaxSize()) {
        // Camera preview with pinch-to-zoom and tap-to-focus
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomDelta, _ ->
                        zoomRatio = (zoomRatio * zoomDelta).coerceIn(
                            QrScannerManager.MIN_ZOOM,
                            QrScannerManager.MAX_ZOOM
                        )
                        qrManager.setZoomRatio(zoomRatio)
                        lastPinchAt = System.currentTimeMillis()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        focusTap = off
                    }
                }
        )

        // Viewfinder + scanline overlay
        ViewfinderOverlay(detected = detected)

        // Once detected, fade-to-black overlay smooths the hand-off to
        // the Pay screen so the camera doesn't snap-cut away. Animates
        // 0 → 1 alpha over 180ms in tandem with the navigation delay
        // above; the success caption and lime corners stay visible
        // through the fade.
        DetectionFadeOverlay(visible = detected)

        // Focus ring animation
        focusTap?.let { tap ->
            FocusRing(at = tap, key = tap.toString(), onDone = { focusTap = null })
        }

        // Top bar: back chip + zoom chip
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackChip(onClick = onClose)
            ZoomChip(
                zoom = zoomRatio,
                onClick = {
                    val next = when {
                        zoomRatio < 1.5f -> 2f
                        zoomRatio < 2.5f -> 3f
                        else -> 1f
                    }
                    zoomRatio = next
                    qrManager.setZoomRatio(zoomRatio)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            )
        }

        // Status text + bottom controls
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional zoom slider during pinch
            if (showZoomSlider) {
                ZoomSlider(zoom = zoomRatio)
                Spacer(Modifier.height(16.dp))
            }
            PointAtQrCaption(detected = detected)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassyCircleButton(
                    icon = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    diameter = 56.dp,
                    onClick = { torchOn = !torchOn }
                )
                GlassyCircleButton(
                    icon = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    diameter = 64.dp,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                GlassyCircleButton(
                    icon = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    diameter = 56.dp,
                    onClick = onOpenFaq
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeoPopAccentCard(accent = NeoPopColors.Accent, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(NeoPopColors.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = NeoPopColors.Accent,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "GRANT CAMERA ACCESS",
                    style = NeoPopType.LabelLarge,
                    color = NeoPopColors.Accent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "OffPay scans UPI QR codes to autofill payment details. " +
                        "Grant camera access to continue.",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (permanentlyDenied) {
            NeoPopPrimaryButton(
                text = "Open App Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            NeoPopPrimaryButton(
                text = "Grant Camera",
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        NeoPopSecondaryButton(
            text = "Cancel",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Viewfinder ────────────────────────────────────────────────────────────────

@Composable
private fun ViewfinderOverlay(detected: Boolean) {
    val infinite = rememberInfiniteTransition(label = "scan")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val cornerColor by animateColorAsState(
        if (detected) NeoPopColors.TextPrimary else NeoPopColors.Accent,
        label = "corner_color"
    )
    val cornerInset by animateFloatAsState(
        targetValue = if (detected) 4f else 0f,
        animationSpec = tween(180),
        label = "corner_inset"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val sideLen = minOf(size.width, size.height) * 0.7f
        val left = (size.width - sideLen) / 2f
        val top = (size.height - sideLen) / 2f
        val sq = androidx.compose.ui.geometry.Rect(
            left + cornerInset.dp.toPx(),
            top + cornerInset.dp.toPx(),
            left + sideLen - cornerInset.dp.toPx(),
            top + sideLen - cornerInset.dp.toPx()
        )

        // Dim everything outside the viewfinder
        val cutout = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRect(sq)
            fillType = PathFillType.EvenOdd
        }
        drawPath(cutout, color = Color(0x99000000)) // 60% black scrim

        // Green flash overlay inside viewfinder when detected
        if (detected) {
            val flash = Path().apply { addRect(sq) }
            drawPath(flash, color = NeoPopColors.Success.copy(alpha = 0.18f))
        }

        // Lime L-corners with subtle idle pulse
        val arm = 32.dp.toPx()
        val thick = 4.dp.toPx()
        val color = cornerColor.copy(alpha = if (detected) 1f else pulse)

        // Top-left
        drawLine(color, sq.topLeft, Offset(sq.left + arm, sq.top), strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(color, sq.topLeft, Offset(sq.left, sq.top + arm), strokeWidth = thick, cap = StrokeCap.Round)
        // Top-right
        drawLine(color, Offset(sq.right - arm, sq.top), sq.topRight, strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(color, sq.topRight, Offset(sq.right, sq.top + arm), strokeWidth = thick, cap = StrokeCap.Round)
        // Bottom-left
        drawLine(color, Offset(sq.left, sq.bottom - arm), sq.bottomLeft, strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(color, sq.bottomLeft, Offset(sq.left + arm, sq.bottom), strokeWidth = thick, cap = StrokeCap.Round)
        // Bottom-right
        drawLine(color, Offset(sq.right - arm, sq.bottom), sq.bottomRight, strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(color, sq.bottomRight, Offset(sq.right, sq.bottom - arm), strokeWidth = thick, cap = StrokeCap.Round)

        // Animated scan line + glow trail (clipped to viewfinder)
        if (!detected) {
            val clip = Path().apply { addRect(sq) }
            clipPath(clip) {
                val y = sq.top + sq.height * sweep
                // Glow trail
                val glowHeight = 24.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeoPopColors.Accent.copy(alpha = 0.18f),
                            NeoPopColors.Accent.copy(alpha = 0.32f),
                            Color.Transparent
                        ),
                        startY = y - glowHeight,
                        endY = y + glowHeight
                    ),
                    topLeft = Offset(sq.left, y - glowHeight),
                    size = androidx.compose.ui.geometry.Size(sq.width, glowHeight * 2)
                )
                // Scan line
                drawLine(
                    color = NeoPopColors.Accent,
                    start = Offset(sq.left + 8f, y),
                    end = Offset(sq.right - 8f, y),
                    strokeWidth = 2f
                )
            }
        }
    }
}

/**
 * Brief fade-to-black overlay shown the moment a QR is detected. Smooths
 * the hand-off from the camera screen to the Pay form so it doesn't feel
 * like a hard snap-cut. ~220ms 0 → 0.55 alpha while we navigate away.
 */
@Composable
private fun DetectionFadeOverlay(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "scan_fade"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    )
}

@Composable
private fun PointAtQrCaption(detected: Boolean) {
    val infinite = rememberInfiniteTransition(label = "caption")
    val pulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caption_pulse"
    )
    Text(
        text = if (detected) "QR DETECTED" else "POINT AT QR",
        style = NeoPopType.LabelLarge,
        color = if (detected) NeoPopColors.Success else NeoPopColors.TextPrimary,
        modifier = Modifier.alpha(if (detected) 1f else pulse)
    )
}

@Composable
private fun FocusRing(at: Offset, key: String, onDone: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "focus_$key",
        finishedListener = { onDone() }
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        // 32 → 24 dp ring (inverse: smaller as progress advances)
        val radius = (32.dp.toPx()) * (1f - progress * 0.25f)
        drawCircle(
            color = NeoPopColors.Accent.copy(alpha = 1f - progress),
            radius = radius,
            center = at,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

// ─── Misc UI helpers ───────────────────────────────────────────────────────────

@Composable
private fun BackChip(onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "back_scale")

    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = NeoPopColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ZoomChip(zoom: Float, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                })
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "%.1f×".format(zoom),
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
    }
}

@Composable
private fun GlassyCircleButton(
    icon: ImageVector,
    contentDescription: String,
    diameter: Dp,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "glassy_scale")

    Box(
        Modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val pressInteraction = androidx.compose.foundation.interaction.PressInteraction.Press(it)
                        interactionSource.tryEmit(pressInteraction)
                        tryAwaitRelease()
                        interactionSource.tryEmit(
                            androidx.compose.foundation.interaction.PressInteraction.Release(pressInteraction)
                        )
                    },
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Lime border
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = NeoPopColors.BorderStrong,
                style = Stroke(width = 1.dp.toPx()),
                radius = size.minDimension / 2f
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NeoPopColors.TextPrimary,
            modifier = Modifier.size(diameter * 0.4f)
        )
    }
}

@Composable
private fun ZoomSlider(zoom: Float) {
    val fraction = ((zoom - QrScannerManager.MIN_ZOOM) /
        (QrScannerManager.MAX_ZOOM - QrScannerManager.MIN_ZOOM)).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth(0.6f)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(NeoPopColors.Accent)
        )
    }
}
