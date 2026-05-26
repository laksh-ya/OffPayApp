package com.offpay.app.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.offpay.app.platform.UssdAccessibilityService

/**
 * Aggregated permission/setup state. Each flag is recomputed when the
 * lifecycle resumes (after the user returns from a system settings page)
 * so the UI immediately reflects what they granted.
 */
data class PermissionStatus(
    val callPhone: Boolean,
    val readPhoneState: Boolean,
    val camera: Boolean,
    val accessibility: Boolean,
    val overlay: Boolean
) {
    /** True when CALL_PHONE + READ_PHONE_STATE are both granted. */
    val phoneBundle: Boolean get() = callPhone && readPhoneState

    /** True when everything required to run an automated session is in place. */
    val readyForOverlayPay: Boolean
        get() = phoneBundle && accessibility && overlay

    /** True when basic dialer-mode payment is possible. */
    val readyForDialerPay: Boolean get() = callPhone
}

/**
 * Reactive snapshot of the host's permission state. Re-checks on every
 * `ON_RESUME`, so when the user grants a permission and returns to the app,
 * the UI updates without a manual refresh.
 */
@Composable
fun rememberPermissionStatus(): MutableState<PermissionStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentStatus(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = currentStatus(context)
            }
        }
        lifecycle.addObserver(observer)
    }

    return state
}

private fun currentStatus(context: Context): PermissionStatus {
    return PermissionStatus(
        callPhone = isGranted(context, Manifest.permission.CALL_PHONE),
        readPhoneState = isGranted(context, Manifest.permission.READ_PHONE_STATE),
        camera = isGranted(context, Manifest.permission.CAMERA),
        accessibility = isAccessibilityServiceEnabled(context),
        overlay = Settings.canDrawOverlays(context)
    )
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and looks for our
 * service's component name. The system uses ":"-separated entries.
 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${UssdAccessibilityService::class.java.canonicalName}"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
}

/** Launches the OS Accessibility settings page. */
fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Same as [openAccessibilitySettings], but also shows a Toast hint that
 * explains how to defeat Android 13+'s "Restricted Settings" lock when
 * the toggle appears greyed out. Most users hit this once and are stuck
 * until they know the menu path.
 */
fun openAccessibilitySettingsWithGuide(context: Context) {
    openAccessibilitySettings(context)
    android.widget.Toast.makeText(
        context,
        "If toggle is greyed out, long-press OffPay icon → App info → ⋮ → Allow restricted settings.",
        android.widget.Toast.LENGTH_LONG
    ).show()
}

/** Launches the OS "Display over other apps" page for our package. */
fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Launches the OS app-info page (for permanently-denied permissions). */
fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Convenience wrapper that returns a launcher for a single runtime permission
 * + a launcher for the bundled phone permissions.
 */
@Composable
fun rememberPermissionLaunchers(
    onResult: (Map<String, Boolean>) -> Unit = {}
): PermissionLaunchers {
    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        onResult
    )
    val single = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(mapOf("single" to granted)) }
    return PermissionLaunchers(multi = multi, single = single)
}

class PermissionLaunchers(
    val multi: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val single: androidx.activity.result.ActivityResultLauncher<String>
) {
    fun requestPhoneBundle() = multi.launch(
        arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
    )

    fun requestCamera() = single.launch(Manifest.permission.CAMERA)
}
