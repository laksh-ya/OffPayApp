package com.offpay.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.offpay.app.platform.OverlayController
import com.offpay.app.platform.UssdEngine

/**
 * Routes USSD session initiation based on the selected [OperationMode].
 *
 * - DIALER: Opens the system dialer with the USSD code prefilled (ACTION_DIAL, no automation).
 * - ADVANCED: Automates the session via [UssdEngine.dial] with the carrier dialog visible.
 * - OVERLAY: Same as Advanced but also shows the branded overlay covering the carrier dialog.
 *
 * Validates: Requirements 4.2, 4.3, 4.4
 */
class ModeRouter(
    private val context: Context,
    private val ussdEngine: UssdEngine,
    private val overlayController: OverlayController?
) {

    /**
     * Starts a USSD session using the appropriate strategy for the given [mode].
     *
     * @param mode The operation mode controlling presentation and automation level.
     * @param code The USSD code to dial (e.g. "*99*1*3#").
     * @return true if the session is automated (Advanced/Overlay), false if only the dialer was opened.
     */
    suspend fun startSession(mode: OperationMode, code: String): Boolean {
        return when (mode) {
            OperationMode.DIALER -> {
                // Open system dialer with prefilled code — no automation, no ACTION_CALL
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                false // not an automated session
            }
            OperationMode.ADVANCED -> {
                // Automate via AccessibilityService; carrier dialog stays visible
                ussdEngine.dial(code)
                true // automated
            }
            OperationMode.OVERLAY -> {
                // Show branded overlay covering carrier dialog, then automate
                overlayController?.show("Processing", "Starting session...", "")
                ussdEngine.dial(code)
                true // automated
            }
        }
    }
}
