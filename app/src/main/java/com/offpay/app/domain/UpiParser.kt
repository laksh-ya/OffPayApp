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

        return UpiData(
            vpa = vpa,
            payeeName = params["pn"]?.let { decodeParam(it) },
            amount = params["am"]?.let { decodeParam(it) },
            transactionNote = params["tn"]?.let { decodeParam(it) }
        )
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
