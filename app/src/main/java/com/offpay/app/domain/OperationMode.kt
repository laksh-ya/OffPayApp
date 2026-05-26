package com.offpay.app.domain

/**
 * The three operation modes controlling how a USSD session is presented.
 *
 * - [AUTO] (default): Full takeover — branded overlay covers the carrier
 *   dialog so the user only ever sees OffPay's UI during the session.
 * - [ADVANCED]: Auto-fills the carrier dialog while keeping it visible,
 *   with a small floating progress chip pinned under the status bar.
 *   The user watches the carrier dialog work in real time.
 * - [MANUAL]: PWA-style fallback — copies the UPI ID to clipboard and
 *   opens the system dialer with *99# prefilled. The user fills in
 *   everything else themselves. No accessibility needed.
 *
 * Order matters here: the UI shows them top-down, and AUTO must be first
 * because it's the default.
 */
enum class OperationMode { AUTO, ADVANCED, MANUAL }
