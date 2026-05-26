package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for PinMasking.maskReply() correctness.
 *
 * **Validates: Requirements 9.3**
 */
class PinMaskingPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 15: PIN Masking Correctness"))

    // Generator for valid PIN strings: 4-6 digits
    val pinArb = arbitrary {
        val length = Arb.int(4..6).bind()
        val digits = (1..length).map { Arb.int(0..9).bind() }
        digits.joinToString("")
    }

    // Generator for reply strings that are NOT equal to the pin
    // Uses printable ASCII strings that differ from the pin
    val nonPinReplyArb = Arb.string(1..20)

    test("maskReply returns masked placeholder when reply equals PIN") {
        checkAll(100, pinArb) { pin ->
            val result = PinMasking.maskReply(pin, pin)
            result shouldBe "••••"
        }
    }

    test("maskReply never exposes original PIN digits when reply equals PIN") {
        checkAll(100, pinArb) { pin ->
            val result = PinMasking.maskReply(pin, pin)
            result.shouldNotContain(pin)
        }
    }

    test("maskReply returns original reply when reply does not equal PIN") {
        checkAll(100, pinArb, nonPinReplyArb) { pin, reply ->
            // Ensure reply != pin for this property
            if (reply != pin) {
                val result = PinMasking.maskReply(reply, pin)
                result shouldBe reply
            }
        }
    }
})
