package com.offpay.app.domain

/**
 * Information about an active SIM card, used for carrier detection.
 */
data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val carrierName: String?,
    val countryIso: String?,
    val mcc: String?,
    val mnc: String?
)
