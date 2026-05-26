package com.offpay.app.domain

/**
 * The three operation modes controlling how a USSD session is presented.
 *
 * - DIALER: Opens system dialer with prefilled code, no automation.
 * - ADVANCED: Automates the session while keeping the carrier dialog visible.
 * - OVERLAY: Covers the carrier dialog with full-screen branded UI.
 */
enum class OperationMode { DIALER, ADVANCED, OVERLAY }
