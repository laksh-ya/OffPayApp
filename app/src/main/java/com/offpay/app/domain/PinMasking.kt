package com.offpay.app.domain

/**
 * Masks PIN values in reply strings for display/logging.
 * Uses a fixed 4-bullet placeholder regardless of actual PIN length
 * to prevent revealing the PIN digit count.
 */
object PinMasking {
    private const val MASKED = "••••"

    /**
     * Returns the masked placeholder if [reply] equals the [pin],
     * otherwise returns the original [reply].
     *
     * @param reply The reply value being sent
     * @param pin The user's UPI PIN
     * @return Masked string if reply is the PIN, otherwise the original reply
     */
    fun maskReply(reply: String, pin: String): String {
        return if (reply == pin) MASKED else reply
    }
}
