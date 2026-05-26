package com.offpay.app.platform

/**
 * Interface for managing OffPay's system-overlay windows (TYPE_APPLICATION_OVERLAY)
 * during a USSD session.
 *
 * Two presentation modes:
 *  - Full overlay ([show] / [update]) covers the carrier dialog.
 *  - Minimal floating bar ([showMinimal] / [updateMinimal]) sits under the
 *    status bar so the carrier dialog stays visible.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4
 */
interface OverlayController {
    /** Whether the overlay can currently be shown (permission granted). */
    fun canShow(): Boolean

    /** Show the full-screen overlay with initial content (AUTO mode). */
    fun show(title: String, subtitle: String, stepLabel: String)

    /** Update the full-screen overlay content in-place without recreating the window. */
    fun update(title: String, subtitle: String, stepLabel: String)

    /**
     * Show the minimal floating top bar (MINIMAL mode). Does NOT cover the
     * carrier dialog — the user still sees and can interact with it.
     *
     * @param progress current step number (0-based or 1-based; used in label)
     * @param total total step count
     * @param label e.g. "PAYING" / "CHECKING BALANCE"
     */
    fun showMinimal(progress: Int, total: Int, label: String)

    /** Update the minimal bar in-place. */
    fun updateMinimal(progress: Int, total: Int, label: String)

    /** Show an error on the active overlay (full or minimal) for [holdMs], then hide. */
    fun showError(title: String, message: String, holdMs: Long = 1500L)

    /** Hide whichever overlay is currently shown. */
    fun hide()

    /** Callback invoked when the user taps cancel on the overlay. */
    var onCancel: (() -> Unit)?

    /** Callback invoked when the user taps the minimal bar (to bring app forward). */
    var onMinimalTapped: (() -> Unit)?
}
