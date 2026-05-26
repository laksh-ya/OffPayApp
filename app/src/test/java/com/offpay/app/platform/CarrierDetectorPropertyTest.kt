package com.offpay.app.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll

/**
 * **Validates: Requirements 10.3, 10.4**
 *
 * Feature: offpay-native-app, Property 17: Carrier Detection Correctness
 *
 * For any carrier name string matching the pattern `/jio|reliance/i`,
 * isUnsupportedCarrier should return true.
 * For any carrier frame text matching any of the "not registered" patterns,
 * isNotRegisteredError should return true.
 */
class CarrierDetectorPropertyTest : FunSpec({

    val jioPattern = CarrierDetector.JIO_PATTERN
    val notRegisteredPatterns = CarrierDetector.NOT_REGISTERED_PATTERNS

    // Helper functions that mirror CarrierDetector logic (pure, no Context needed)
    fun isUnsupportedCarrier(carrierName: String): Boolean =
        jioPattern.containsMatchIn(carrierName)

    fun isNotRegisteredError(text: String): Boolean =
        notRegisteredPatterns.any { it.containsMatchIn(text) }

    // --- Generators for isUnsupportedCarrier ---

    // Generator: strings that contain "jio" or "reliance" in various case combinations
    val unsupportedCarrierArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val keyword = if (random.nextBoolean()) "jio" else "reliance"
        // Randomize case of each character
        val cased = keyword.map { c ->
            if (random.nextBoolean()) c.uppercaseChar() else c.lowercaseChar()
        }.joinToString("")
        // Embed in random surrounding text
        val prefixLen = random.nextInt(0, 20)
        val suffixLen = random.nextInt(0, 20)
        val prefix = (0 until prefixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        "$prefix$cased$suffix"
    }

    // Generator: strings that do NOT contain "jio" or "reliance" (case-insensitive)
    val supportedCarrierArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val carriers = listOf("Airtel", "Vodafone", "BSNL", "Vi", "MTNL", "Idea", "Tata")
        val base = carriers[random.nextInt(carriers.size)]
        val suffixLen = random.nextInt(0, 10)
        val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        var result = "$base$suffix"
        // Ensure no accidental jio/reliance match
        if (result.lowercase().contains("jio") || result.lowercase().contains("reliance")) {
            result = result.replace(Regex("jio|reliance", RegexOption.IGNORE_CASE), "xxx")
        }
        result
    }

    // --- Generators for isNotRegisteredError ---

    // Generator: strings matching one of the NOT_REGISTERED_PATTERNS
    val notRegisteredTextArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val templates = listOf(
            "could not find your bank",
            "could not find ur bank",
            "Could Not Find Your Bank account",
            "is not a valid selection",
            "Is Not A Valid Selection",
            "please enter the correct no",
            "Please Enter The Correct No",
            "bank not found",
            "no bank linked",
            "no bank found",
            "Bank Not Found in our records",
            "No Bank Linked to this number"
        )
        val template = templates[random.nextInt(templates.size)]
        // Optionally add surrounding text
        val prefixLen = random.nextInt(0, 15)
        val suffixLen = random.nextInt(0, 15)
        val prefix = (0 until prefixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        "$prefix $template $suffix".trim()
    }

    // Generator: strings that do NOT match any "not registered" pattern
    val registeredTextArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val templates = listOf(
            "Transaction successful",
            "Payment sent to merchant",
            "Balance is Rs. 5000",
            "Enter UPI PIN",
            "Select your bank",
            "Welcome to USSD banking",
            "Enter amount to transfer",
            "Your request is being processed"
        )
        val base = templates[random.nextInt(templates.size)]
        val suffixLen = random.nextInt(0, 10)
        val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
        var result = "$base $suffix".trim()
        // Guard: ensure no accidental pattern match
        if (notRegisteredPatterns.any { it.containsMatchIn(result) }) {
            result = "safe text without any matching patterns"
        }
        result
    }

    test("Feature: offpay-native-app, Property 17: Carrier Detection Correctness") {
        // Sub-property 1: Strings containing jio/reliance -> isUnsupportedCarrier returns true
        forAll(100, unsupportedCarrierArb) { carrierName ->
            isUnsupportedCarrier(carrierName)
        }

        // Sub-property 2: Strings NOT containing jio/reliance -> isUnsupportedCarrier returns false
        forAll(100, supportedCarrierArb) { carrierName ->
            !isUnsupportedCarrier(carrierName)
        }

        // Sub-property 3: Strings matching "not registered" patterns -> isNotRegisteredError returns true
        forAll(100, notRegisteredTextArb) { text ->
            isNotRegisteredError(text)
        }

        // Sub-property 4: Strings NOT matching patterns -> isNotRegisteredError returns false
        forAll(100, registeredTextArb) { text ->
            !isNotRegisteredError(text)
        }
    }
})
