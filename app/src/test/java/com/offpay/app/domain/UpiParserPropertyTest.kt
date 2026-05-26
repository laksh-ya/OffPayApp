package com.offpay.app.domain

import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.net.URLEncoder

/**
 * Property-based tests for UpiParser.parse() round-trip correctness.
 *
 * **Validates: Requirements 1.2**
 */
class UpiParserPropertyTest : FunSpec({

    tags(Tag("Feature: offpay-native-app, Property 1: UPI URI Parsing Round-Trip"))

    // Characters valid for VPA parts: [a-zA-Z0-9.\-_]
    val vpaChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '-', '_')

    // Generator for VPA part strings (3+ chars from valid charset)
    fun vpaPartArb(minLen: Int = 3, maxLen: Int = 10): Arb<String> = arbitrary {
        val len = Arb.int(minLen..maxLen).bind()
        buildString {
            repeat(len) {
                val idx = Arb.int(0 until vpaChars.size).bind()
                append(vpaChars[idx])
            }
        }
    }

    // Generator for valid VPAs: local@handle
    val vpaArb = arbitrary {
        val local = vpaPartArb(3, 10).bind()
        val handle = vpaPartArb(3, 8).bind()
        "$local@$handle"
    }

    // Characters safe for URI query parameter values (printable ASCII minus &, =, #, ?)
    val safeChars = (32..126).map { it.toChar() }.filter { it !in setOf('&', '=', '#', '?') }

    // Generator for safe strings (used for payee name and notes)
    fun safeStringArb(minLen: Int = 1, maxLen: Int = 20): Arb<String> = arbitrary {
        val len = Arb.int(minLen..maxLen).bind()
        buildString {
            repeat(len) {
                val idx = Arb.int(0 until safeChars.size).bind()
                append(safeChars[idx])
            }
        }
    }

    // Generator for optional payee names
    val payeeNameArb: Arb<String?> = arbitrary {
        val include = Arb.boolean().bind()
        if (include) safeStringArb(1, 20).bind() else null
    }

    // Generator for optional amounts (valid decimal format)
    val amountArb: Arb<String?> = arbitrary {
        val include = Arb.boolean().bind()
        if (include) {
            val whole = Arb.int(1..99999).bind()
            val hasDecimal = Arb.boolean().bind()
            if (hasDecimal) {
                val decimal = Arb.int(1..99).bind()
                "$whole.${decimal.toString().padStart(2, '0')}"
            } else {
                "$whole"
            }
        } else {
            null
        }
    }

    // Generator for optional transaction notes
    val noteArb: Arb<String?> = arbitrary {
        val include = Arb.boolean().bind()
        if (include) safeStringArb(1, 30).bind() else null
    }

    test("UPI URI parsing round-trip: parsed fields match encoded parameters") {
        checkAll(100, vpaArb, payeeNameArb, amountArb, noteArb) { vpa, payeeName, amount, note ->
            // Build a valid UPI URI with URL-encoded parameters
            val params = mutableListOf("pa=${URLEncoder.encode(vpa, "UTF-8")}")
            if (payeeName != null) params.add("pn=${URLEncoder.encode(payeeName, "UTF-8")}")
            if (amount != null) params.add("am=${URLEncoder.encode(amount, "UTF-8")}")
            if (note != null) params.add("tn=${URLEncoder.encode(note, "UTF-8")}")

            val uri = "upi://pay?${params.joinToString("&")}"

            // Parse the URI
            val result = UpiParser.parse(uri)

            // Verify round-trip: all fields should match the inputs
            result shouldBe UpiData(
                vpa = vpa,
                payeeName = payeeName,
                amount = amount,
                transactionNote = note
            )
        }
    }
})
