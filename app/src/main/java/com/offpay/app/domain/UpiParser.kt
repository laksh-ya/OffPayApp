package com.offpay.app.domain

import java.net.URLDecoder

/**
 * Parses `upi://pay?` URIs and validates/extracts VPA from text.
 * All functions are pure with no side effects.
 */
object UpiParser {

    private val VPA_REGEX = Regex("[a-zA-Z0-9.\\-_]{3,}@[a-zA-Z0-9.\\-_]{3,}")

    /**
     * Parses a `upi://pay?` URI string and extracts payment parameters.
     * Handles URL-encoded parameters (e.g., %40 → @).
     *
     * @return UpiData if the URI is valid and contains a valid VPA, null otherwise.
     */
    fun parse(raw: String): UpiData? {
        val trimmed = raw.trim()
        if (!trimmed.lowercase().startsWith("upi://pay?")) return null

        val queryString = trimmed.substringAfter("?", "")
        if (queryString.isEmpty()) return null

        val params = parseQueryParams(queryString)

        val vpa = params["pa"]?.let { decodeParam(it) } ?: return null
        if (!isValidVpa(vpa)) return null

        val rawAmount = params["am"]?.let { decodeParam(it) }
        val sanitizedAmount = sanitizeAmount(rawAmount)

        // Workaround for Dynamic QRs: 
        // Dynamic QRs use 'tr' (Transaction Ref) or 'tid' (Txn ID) to link the payment 
        // to an order. *99# doesn't have a dedicated field for these, but we can 
        // try passing them in the 'tn' (Note) field as a best-effort workaround.
        // We limit to 20 chars as many USSD gateways truncate remarks anyway.
        val note = (params["tn"] ?: params["tr"] ?: params["tid"])
            ?.let { decodeParam(it) }
            ?.take(20)

        return UpiData(
            vpa = vpa,
            payeeName = params["pn"]?.let { decodeParam(it) },
            amount = sanitizedAmount,
            transactionNote = note
        )
    }

    private fun sanitizeAmount(amount: String?): String? {
        if (amount == null) return null
        return try {
            // Strip trailing .00 or .0 which often break USSD integer inputs
            if (amount.contains(".")) {
                val d = amount.toDouble()
                if (d == d.toLong().toDouble()) {
                    d.toLong().toString()
                } else {
                    "%.2f".format(d)
                }
            } else {
                amount
            }
        } catch (_: Exception) {
            amount
        }
    }

    /**
     * Validates whether the given string is a valid VPA format.
     * Pattern: [a-zA-Z0-9.\-_]{3,}@[a-zA-Z0-9.\-_]{3,}
     */
    fun isValidVpa(vpa: String): Boolean {
        return VPA_REGEX.matches(vpa.trim())
    }

    /**
     * Extracts the first VPA match from an arbitrary text string.
     *
     * @return The first valid VPA found in the text, or null if none found.
     */
    fun extractVpaFromText(text: String): String? {
        return VPA_REGEX.find(text)?.value
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0].lowercase() to parts[1]
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun decodeParam(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }
}
