package com.offpay.app.platform

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.offpay.app.domain.SimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects the active SIM carrier and applies fail-fast rules for unsupported
 * carriers and "not registered" error patterns.
 */
class CarrierDetector(private val context: Context) {

    /**
     * Reads the active SIM's carrier information via SubscriptionManager/TelephonyManager.
     * Returns null if no SIM is present or READ_PHONE_STATE permission is not granted.
     */
    suspend fun getActiveSimInfo(): SimInfo? = withContext(Dispatchers.IO) {
        try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    ?: return@withContext fallbackFromTelephonyManager()

            val activeSubscriptions = try {
                subscriptionManager.activeSubscriptionInfoList
            } catch (e: SecurityException) {
                // READ_PHONE_STATE not granted
                return@withContext fallbackFromTelephonyManager()
            }

            if (activeSubscriptions.isNullOrEmpty()) {
                return@withContext fallbackFromTelephonyManager()
            }

            // Use the first active subscription (default data SIM)
            val info = activeSubscriptions[0]
            SimInfo(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                carrierName = info.carrierName?.toString(),
                countryIso = info.countryIso,
                mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.mccString
                } else {
                    @Suppress("DEPRECATION")
                    info.mcc.toString()
                },
                mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.mncString
                } else {
                    @Suppress("DEPRECATION")
                    info.mnc.toString()
                }
            )
        } catch (e: Exception) {
            fallbackFromTelephonyManager()
        }
    }

    /**
     * Fallback: use TelephonyManager when SubscriptionManager is unavailable.
     */
    private fun fallbackFromTelephonyManager(): SimInfo? {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return null

        val carrierName = telephonyManager.networkOperatorName
        if (carrierName.isNullOrBlank()) return null

        return SimInfo(
            slotIndex = 0,
            subscriptionId = -1,
            carrierName = carrierName,
            countryIso = telephonyManager.networkCountryIso,
            mcc = telephonyManager.networkOperator?.take(3),
            mnc = telephonyManager.networkOperator?.drop(3)
        )
    }

    /**
     * Returns true if the given carrier name matches Jio/Reliance, which does not
     * reliably support *99# USSD service.
     *
     * Validates: Requirements 10.3
     */
    fun isUnsupportedCarrier(carrierName: String): Boolean {
        return JIO_PATTERN.containsMatchIn(carrierName)
    }

    /**
     * Returns true if the given text matches any of the "not registered for *99#"
     * error patterns from the carrier, indicating the user needs to link their
     * bank account.
     *
     * Validates: Requirements 10.4
     */
    fun isNotRegisteredError(text: String): Boolean {
        return NOT_REGISTERED_PATTERNS.any { it.containsMatchIn(text) }
    }

    companion object {
        val JIO_PATTERN = Regex("jio|reliance", RegexOption.IGNORE_CASE)
        val NOT_REGISTERED_PATTERNS = listOf(
            Regex("could\\s+not\\s+find\\s+(your|ur)\\s+bank", RegexOption.IGNORE_CASE),
            Regex("is\\s+not\\s+a\\s+valid\\s+selection", RegexOption.IGNORE_CASE),
            Regex("please\\s+enter\\s+the\\s+correct\\s+no", RegexOption.IGNORE_CASE),
            Regex("bank\\s+not\\s+found|no\\s+bank\\s+(linked|found)", RegexOption.IGNORE_CASE)
        )
    }
}
