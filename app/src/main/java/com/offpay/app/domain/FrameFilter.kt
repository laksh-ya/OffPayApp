package com.offpay.app.domain

/**
 * Filters USSD carrier frames by deduplicating consecutive identical text
 * and suppressing system placeholder/filler frames.
 *
 * Validates: Requirements 6.2, 6.3
 */
class FrameFilter {

    private var lastEmittedText: String? = null

    companion object {
        private val PLACEHOLDER_PATTERNS = listOf(
            "please wait",
            "processing",
            "loading",
            "connecting",
            "ussd code running"
        )
    }

    /**
     * Returns true if the given text matches a known system placeholder pattern.
     * Matching is case-insensitive and performed on trimmed text.
     */
    fun isSystemPlaceholder(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return PLACEHOLDER_PATTERNS.any { pattern ->
            normalized == pattern ||
                normalized.startsWith(pattern) ||
                normalized.startsWith("$pattern…") ||
                normalized.startsWith("$pattern...")
        }
    }

    /**
     * Returns true if the given text is identical to the last emitted text
     * (case-sensitive comparison on trimmed content).
     */
    fun isDuplicate(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed == lastEmittedText
    }

    /**
     * Determines whether a frame with the given text should be emitted.
     * Returns false if the text is a system placeholder or a duplicate of the last emitted frame.
     * If true, updates the last emitted text state.
     */
    fun shouldEmit(text: String): Boolean {
        val trimmed = text.trim()
        if (isSystemPlaceholder(trimmed)) return false
        if (trimmed == lastEmittedText) return false
        lastEmittedText = trimmed
        return true
    }

    /**
     * Resets the deduplication state. Call this when starting a new USSD session.
     */
    fun reset() {
        lastEmittedText = null
    }
}
