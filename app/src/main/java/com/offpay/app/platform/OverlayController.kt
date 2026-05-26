package com.offpay.app.platform

/**
 * Interface for managing the TYPE_APPLICATION_OVERLAY window that covers
 * the carrier USSD dialog in Overlay mode.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4
 */
interface OverlayController {
    /** Whether the overlay can currently be shown (permission granted). */
    fun canShow(): Boolean

    /** Show the overlay with initial content. */
    fun show(title: String, subtitle: String, stepLabel: String)

    /** Update the overlay content in-place without recreating the window. */
    fun update(title: String, subtitle: String, stepLabel: String)

    /** Show an error on the overlay for the given hold duration, then hide. */
    fun showError(title: String, message: String, holdMs: Long = 1200L)

    /** Hide the overlay window. */
    fun hide()

    /** Callback invoked when the user taps the cancel button on the overlay. */
    var onCancel: (() -> Unit)?
}
