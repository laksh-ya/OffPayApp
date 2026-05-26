package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for FrameFilter deduplication and filler suppression.
 *
 * **Validates: Requirements 6.2, 6.3**
 */
class FrameFilterPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 11: Frame Deduplication and Filler Suppression"))

    // System placeholder patterns that should be suppressed
    val placeholderTexts = listOf(
        "please wait",
        "Please Wait",
        "PLEASE WAIT",
        "processing",
        "Processing...",
        "processing…",
        "loading",
        "Loading",
        "connecting",
        "Connecting...",
        "ussd code running",
        "USSD Code Running",
        "please wait while we process",
        "loading your request"
    )

    // Valid frame texts that should pass through
    val validTexts = listOf(
        "Enter UPI ID",
        "Enter Amount",
        "Enter UPI PIN",
        "Payment successful",
        "Balance: Rs 1000",
        "Select your bank",
        "Transaction ID: 123456",
        "Confirm payment of Rs 500 to user@bank?",
        "Press 1 to confirm",
        "Your request has been processed"
    )

    // Generator for frame text sequences: mix of valid texts, duplicates, and placeholders
    val frameSequenceArb = arbitrary {
        val length = Arb.int(1..30).bind()
        val allChoices = validTexts + placeholderTexts
        val frames = mutableListOf<String>()
        for (i in 0 until length) {
            // Randomly pick: valid text, duplicate of previous, or placeholder
            val choice = Arb.int(0..2).bind()
            when {
                choice == 0 || frames.isEmpty() -> {
                    // Pick a random text from all choices
                    frames.add(Arb.element(allChoices).bind())
                }
                choice == 1 && frames.isNotEmpty() -> {
                    // Duplicate of the last frame
                    frames.add(frames.last())
                }
                else -> {
                    // Pick a placeholder
                    frames.add(Arb.element(placeholderTexts).bind())
                }
            }
        }
        frames.toList()
    }

    test("emitted frames contain no consecutive duplicates and no system placeholders") {
        checkAll(100, frameSequenceArb) { frameTexts ->
            val filter = FrameFilter()
            val emitted = mutableListOf<String>()

            for (text in frameTexts) {
                if (filter.shouldEmit(text)) {
                    emitted.add(text.trim())
                }
            }

            // Property 1: No two consecutive emitted frames have identical text
            for (i in 1 until emitted.size) {
                (emitted[i] != emitted[i - 1]) shouldBe true
            }

            // Property 2: No emitted frame matches a system placeholder pattern
            for (frame in emitted) {
                filter.isSystemPlaceholder(frame) shouldBe false
            }
        }
    }
})
