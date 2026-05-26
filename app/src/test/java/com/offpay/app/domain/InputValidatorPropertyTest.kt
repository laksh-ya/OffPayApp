package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.kotest.core.Tag

/**
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
 *
 * Property 12: Input Validation Composite Correctness
 *
 * For any combination of (vpa, amount, pin) strings, the FormValidationResult should report
 * errors for exactly those fields that fail their individual validation rules.
 */
class InputValidatorPropertyTest : FunSpec({

    tags(Tag("Feature: offpay-native-app, Property 12: Input Validation Composite Correctness"))

    // Generator for arbitrary strings that mix valid and invalid VPA/amount/PIN inputs
    val arbVpaInput = Arb.of(
        // Valid VPAs
        "user@bank",
        "test.user@okaxis",
        "a-b_c@upi",
        "valid123@ybl",
        // Invalid VPAs
        "",
        "   ",
        "noatsign",
        "@bank",
        "user@",
        "user@@bank",
        "special!char@bank",
        "a".repeat(51) + "@bank",
        "user name@bank",
        "user@ban k"
    )

    val arbAmountInput = Arb.of(
        // Valid amounts
        "1",
        "1.00",
        "100.50",
        "5000",
        "5000.00",
        "2999.99",
        // Invalid amounts
        "",
        "0",
        "0.00",
        "0.01",
        "0.99",
        "0.001",
        "5000.01",
        "5001",
        "-1",
        "abc",
        "10.123",
        "1,000",
        "12.345"
    )

    val arbPinInput = Arb.of(
        // Valid PINs
        "1234",
        "12345",
        "123456",
        "0000",
        "999999",
        // Invalid PINs
        "",
        "123",
        "1234567",
        "abcd",
        "12ab",
        "12 34",
        "123!",
        "12345678"
    )

    test("composite validation reports errors for exactly those fields that fail individually") {
        checkAll(100, arbVpaInput, arbAmountInput, arbPinInput) { vpa, amount, pin ->
            val compositeResult = InputValidator.validatePaymentForm(vpa, amount, pin)
            val vpaResult = InputValidator.validateVpa(vpa)
            val amountResult = InputValidator.validateAmount(amount)
            val pinResult = InputValidator.validatePin(pin)

            // VPA field should have error iff individual validation fails
            val expectedVpaError = !vpaResult.isValid
            val actualVpaError = compositeResult.errors.containsKey(FormField.VPA)
            actualVpaError shouldBe expectedVpaError

            // Amount field should have error iff individual validation fails
            val expectedAmountError = !amountResult.isValid
            val actualAmountError = compositeResult.errors.containsKey(FormField.AMOUNT)
            actualAmountError shouldBe expectedAmountError

            // PIN field should have error iff individual validation fails
            val expectedPinError = !pinResult.isValid
            val actualPinError = compositeResult.errors.containsKey(FormField.PIN)
            actualPinError shouldBe expectedPinError

            // Error messages should match exactly
            if (expectedVpaError) {
                compositeResult.errors[FormField.VPA] shouldBe vpaResult.errorMessage
            }
            if (expectedAmountError) {
                compositeResult.errors[FormField.AMOUNT] shouldBe amountResult.errorMessage
            }
            if (expectedPinError) {
                compositeResult.errors[FormField.PIN] shouldBe pinResult.errorMessage
            }
        }
    }

    test("composite validation with fully random strings reports errors matching individual validators") {
        checkAll(100, Arb.string(0..60), Arb.string(0..20), Arb.string(0..10)) { vpa, amount, pin ->
            val compositeResult = InputValidator.validatePaymentForm(vpa, amount, pin)
            val vpaResult = InputValidator.validateVpa(vpa)
            val amountResult = InputValidator.validateAmount(amount)
            val pinResult = InputValidator.validatePin(pin)

            // The composite result should contain errors for EXACTLY the fields that fail individually
            val expectedErrorFields = mutableSetOf<FormField>()
            if (!vpaResult.isValid) expectedErrorFields.add(FormField.VPA)
            if (!amountResult.isValid) expectedErrorFields.add(FormField.AMOUNT)
            if (!pinResult.isValid) expectedErrorFields.add(FormField.PIN)

            compositeResult.errors.keys shouldBe expectedErrorFields
        }
    }
})
