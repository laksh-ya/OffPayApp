package com.offpay.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.offpay.app.platform.OverlayController
import com.offpay.app.platform.UssdEngine

/**
 * Routes USSD session initiation based on the selected [OperationMode].
 *
 * - [OperationMode.MANUAL]: Opens the system dialer (ACTION_DIAL) with the
 *   USSD code prefilled. No automation. The clipboard copy and snackbar
 *   are handled at the ViewModel layer (where the VPA is in scope).
 * - [OperationMode.ADVANCED]: Automates the session via [UssdEngine.dial]
 *   and shows a small floating progress chip via
 *   [OverlayController.showMinimal]; the carrier dialog stays visible.
 * - [OperationMode.AUTO]: Same automation, plus the full-screen branded
 *   overlay via [OverlayController.show] that hides the carrier dialog.
 */
class ModeRouter(
    private val context: Context,
    private val ussdEngine: UssdEngine,
    private val overlayController: OverlayController?
) {

    /**
     * Starts a USSD session using the appropriate strategy for the given [mode].
     *
     * @return true if the session is automated (Advanced/Auto), false for Manual.
     */
    suspend fun startSession(
        mode: OperationMode,
        code: String,
        title: String = "Processing",
        subtitle: String = "Starting session..."
    ): Boolean {
        return when (mode) {
            OperationMode.MANUAL -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                false
            }
            OperationMode.ADVANCED -> {
                overlayController?.showMinimal(progress = 0, total = 1, label = title)
                ussdEngine.dial(code)
                true
            }
            OperationMode.AUTO -> {
                overlayController?.show(title, subtitle, "")
                ussdEngine.dial(code)
                true
            }
        }
    }
}
