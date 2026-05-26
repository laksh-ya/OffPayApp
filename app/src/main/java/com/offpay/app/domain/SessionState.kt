package com.offpay.app.domain

/**
 * Represents the current state of a USSD session as observed by the UI layer.
 */
sealed class SessionState {
    object Idle : SessionState()
    data class Running(val label: String, val stepIndex: Int, val total: Int) : SessionState()
    data class Success(val resultText: String) : SessionState()
    data class Failed(val message: String, val resultText: String) : SessionState()
}
