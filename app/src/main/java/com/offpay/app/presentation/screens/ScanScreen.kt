package com.offpay.app.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.offpay.app.platform.QrScannerManager
import com.offpay.app.presentation.permissions.openAppDetailsSettings
import com.offpay.app.presentation.ui.components.NeoPopAccentCard
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.components.NeoPopSecondaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import kotlinx.coroutines.launch

/**
 * Full-screen QR scanner. Pure black canvas, lime-tinted L-corner viewfinder,
 * pinch-to-zoom, tap-to-focus animation, and a gallery picker fallback.
 *
 * On detection, fires haptic feedback + freezes the scan-line green, then
 * calls [onResult] with the raw QR text. The caller is responsible for
 * navigating away.
 */
@Composable
fun ScanScreen(
    qrManager: QrScannerManager,
    onResult: (String) -> Unit,
    onClose: () -> Unit,
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

    Box(modifier.fillMaxSize().background(NeoPopColors.Black)) {
        when {
            hasCameraPermission -> CameraScannerContent(
                qrManager = qrManager,
                onResult = onResult,
                onClose = onClose
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(false) }
    var focusTap by remember { mutableStateOf<Offset?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                qrManager.decodeFromUri(context, uri)?.let { raw ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    detected = true
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
                onResult(raw)
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

        // Focus ring animation
        focusTap?.let { tap ->
            FocusRing(at = tap, key = tap.toString(), onDone = { focusTap = null })
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(icon = Icons.Default.Close, contentDescription = "Close", onClick = onClose)
            ZoomChip(zoom = zoomRatio)
        }

        // Status text + bottom controls
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (detected) "QR DETECTED" else "POINT AT QR CODE",
                style = NeoPopType.LabelLarge,
                color = if (detected) NeoPopColors.Success else NeoPopColors.TextPrimary
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    onClick = { torchOn = !torchOn /* CameraX torch wiring is on the manager */ }
                )
                CircleIconButton(
                    icon = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
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
        NeoPopAccentCard(accent = NeoPopColors.Warn, modifier = Modifier.fillMaxWidth()) {
            Column {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = NeoPopColors.Warn,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "CAMERA NEEDED",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Warn
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
                text = "Open Settings",
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
    val infinite = rememberInfiniteTransition(label = "scanline")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val sideLen = minOf(size.width, size.height) * 0.7f
        val left = (size.width - sideLen) / 2f
        val top = (size.height - sideLen) / 2f
        val sq = androidx.compose.ui.geometry.Rect(left, top, left + sideLen, top + sideLen)

        // Dim everything outside the viewfinder
        val cutout = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRect(sq)
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(cutout, color = NeoPopColors.Overlay)

        // Lime L-corners (16dp arms, 4dp thickness)
        val arm = 28.dp.toPx()
        val thick = 4.dp.toPx()
        val color = if (detected) NeoPopColors.Success else NeoPopColors.Accent

        // Top-left
        drawLine(color, sq.topLeft, Offset(sq.left + arm, sq.top), strokeWidth = thick)
        drawLine(color, sq.topLeft, Offset(sq.left, sq.top + arm), strokeWidth = thick)
        // Top-right
        drawLine(color, Offset(sq.right - arm, sq.top), sq.topRight, strokeWidth = thick)
        drawLine(color, sq.topRight, Offset(sq.right, sq.top + arm), strokeWidth = thick)
        // Bottom-left
        drawLine(color, Offset(sq.left, sq.bottom - arm), sq.bottomLeft, strokeWidth = thick)
        drawLine(color, sq.bottomLeft, Offset(sq.left + arm, sq.bottom), strokeWidth = thick)
        // Bottom-right
        drawLine(color, Offset(sq.right - arm, sq.bottom), sq.bottomRight, strokeWidth = thick)
        drawLine(color, sq.bottomRight, Offset(sq.right, sq.bottom - arm), strokeWidth = thick)

        // Animated scan line (clipped to viewfinder)
        if (!detected) {
            val clip = Path().apply { addRect(sq) }
            clipPath(clip) {
                val y = sq.top + sq.height * sweep
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

@Composable
private fun FocusRing(at: Offset, key: String, onDone: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 350),
        label = "focus_$key",
        finishedListener = { onDone() }
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = (24.dp.toPx()) * (1f + (1f - progress) * 0.6f)
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
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        Modifier
            .size(48.dp)
            .background(NeoPopColors.SurfaceHigh)
    ) {
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = NeoPopColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ZoomChip(zoom: Float) {
    Box(
        Modifier
            .background(NeoPopColors.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "%.1f×".format(zoom),
            style = NeoPopType.LabelMedium,
            color = NeoPopColors.Accent
        )
    }
}
