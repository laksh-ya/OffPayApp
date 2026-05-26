package com.offpay.app.domain

/**
 * Data extracted from a UPI QR code or deep link.
 */
data class UpiData(
    val vpa: String,
    val payeeName: String?,
    val amount: String?,
    val transactionNote: String?
)
