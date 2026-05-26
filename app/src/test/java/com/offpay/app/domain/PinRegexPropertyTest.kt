package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for PIN-prompt regex no false positives.
 *
 * **Validates: Requirements 9.5**
 *
 * For any string containing "PIN" only as a substring within other words
 * (e.g., "SPINNING", "PINCODE", "OPINION") and NOT as a standalone word
 * matching \bPIN\b or \bupi\s*pin\b, the PIN-prompt step regex should NOT match.
 */
class PinRegexPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 16: PIN Regex No False Positives"))

    // The PIN-prompt regex from SendUpi action step
    val pinPromptRegex = Regex("\\bupi\\s*pin\\b|\\b(enter|6\\s*digit).*pin\\b", RegexOption.IGNORE_CASE)

    // Words that contain "PIN" as a substring but NOT as a standalone word
    val wordsWithEmbeddedPin = listOf(
        "SPINNING", "PINCODE", "OPINION", "ALPINE", "PINEAPPLE",
        "PINCH", "OPINING", "PINTO", "PINING", "LUPINE",
        "PINBALL", "PINSTRIPE", "HAIRPIN", "KINGPIN", "PUSHPIN",
        "LINCHPIN", "CRISPIN", "CHOPIN", "RAPINE", "SUPINE",
        "PINCUSHION", "PINDER", "PINKISH", "SPINACH", "SPINDLE"
    )

    // Generator for words containing embedded PIN (not standalone)
    val embeddedPinWordArb = Arb.element(wordsWithEmbeddedPin)

    // Generator for filler words that don't trigger the regex
    val fillerWords = listOf(
        "please", "your", "the", "transaction", "amount", "bank",
        "account", "number", "code", "select", "option", "menu",
        "service", "request", "payment", "balance", "transfer"
    )
    val fillerWordArb = Arb.element(fillerWords)

    // Generator for sentences containing embedded-PIN words but no standalone PIN
    val embeddedPinSentenceArb = arbitrary {
        val numWords = Arb.int(2..5).bind()
        val words = mutableListOf<String>()

        // Add at least one word with embedded PIN
        words.add(embeddedPinWordArb.bind())

        // Add filler words
        repeat(numWords) {
            words.add(fillerWordArb.bind())
        }

        // Shuffle to randomize position
        words.shuffle()
        words.joinToString(" ")
    }

    // Validation regexes to ensure our generated strings don't accidentally
    // contain standalone PIN or "upi pin"
    val standalonePinRegex = Regex("\\bPIN\\b", RegexOption.IGNORE_CASE)
    val upiPinRegex = Regex("\\bupi\\s*pin\\b", RegexOption.IGNORE_CASE)

    test("PIN-prompt regex should NOT match strings with PIN only embedded in other words") {
        checkAll(100, embeddedPinSentenceArb) { sentence ->
            // Pre-condition: ensure the generated string does NOT contain
            // standalone PIN or "upi pin" (our generator shouldn't produce these,
            // but we filter to be safe)
            val hasStandalonePin = standalonePinRegex.containsMatchIn(sentence)
            val hasUpiPin = upiPinRegex.containsMatchIn(sentence)

            if (!hasStandalonePin && !hasUpiPin) {
                // The PIN-prompt regex should NOT match
                val matches = pinPromptRegex.containsMatchIn(sentence)
                matches shouldBe false
            }
        }
    }
})
