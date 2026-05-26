package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll

/**
 * **Validates: Requirements 1.4**
 *
 * Feature: offpay-native-app, Property 2: Invalid Input Rejection
 *
 * For any string that does not contain a valid `upi://pay?` URI and does not contain
 * a substring matching the VPA pattern `[a-zA-Z0-9.\-_]{3,}@[a-zA-Z0-9.\-_]{3,}`,
 * the UpiParser.parse() function should return null.
 */
class UpiParserInvalidInputPropertyTest : FunSpec({

    val vpaPattern = Regex("[a-zA-Z0-9.\\-_]{3,}@[a-zA-Z0-9.\\-_]{3,}")

    // Characters that cannot form a VPA pattern (non-alphanumeric, not '.', '-', '_', '@')
    val safeChars = charArrayOf(
        ' ', '!', '#', '$', '%', '^', '&', '*', '(', ')', '+', '=',
        '[', ']', '{', '}', '|', '/', '~', '`', '<', '>', ',', '?', ';', ':'
    )

    // Generator for strings that do NOT start with "upi://pay?" and do NOT contain VPA pattern
    val invalidInputArb: Arb<String> = arbitrary { rs ->
        val random = rs.random

        val candidate = when (random.nextInt(5)) {
            // Strategy 1: Pure safe characters (guaranteed no VPA)
            0 -> {
                val len = random.nextInt(0, 50)
                (0 until len).map { safeChars[random.nextInt(safeChars.size)] }.joinToString("")
            }
            // Strategy 2: Short alphanumeric before @ (< 3 chars left side, can't form VPA)
            1 -> {
                val prefixLen = random.nextInt(0, 3)
                val prefix = (0 until prefixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
                val suffixLen = random.nextInt(0, 3)
                val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
                "${prefix}@${suffix}"
            }
            // Strategy 3: Alphanumeric without @ symbol (no VPA without @)
            2 -> {
                val len = random.nextInt(0, 30)
                (0 until len).map { ('a' + random.nextInt(26)) }.joinToString("")
            }
            // Strategy 4: Long left side but short right side of @ (< 3 right chars)
            3 -> {
                val prefixLen = random.nextInt(3, 15)
                val prefix = (0 until prefixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
                val suffixLen = random.nextInt(0, 3)
                val suffix = (0 until suffixLen).map { ('a' + random.nextInt(26)) }.joinToString("")
                "${prefix}@${suffix}"
            }
            // Strategy 5: Empty and whitespace
            else -> {
                " ".repeat(random.nextInt(0, 10))
            }
        }

        // Final guard: ensure no accidental upi://pay? prefix or VPA match
        var result = candidate
        if (result.lowercase().startsWith("upi://pay?")) {
            result = "x$result"
        }
        if (vpaPattern.containsMatchIn(result)) {
            result = result.replace(Regex("[a-zA-Z0-9.\\-_@]"), "!")
        }
        result
    }

    test("Feature: offpay-native-app, Property 2: Invalid Input Rejection") {
        forAll(100, invalidInputArb) { input ->
            // Precondition: input does not start with upi://pay? and does not contain VPA pattern
            !input.lowercase().startsWith("upi://pay?") &&
                !vpaPattern.containsMatchIn(input) &&
                // Property: parse should return null for all such invalid inputs
                UpiParser.parse(input) == null
        }
    }
})
