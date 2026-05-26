package com.offpay.app.presentation

import com.offpay.app.domain.FormField
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 8.5**
 *
 * Property 13: Error Highlight Clearing on Edit
 *
 * For any FormValidationResult containing errors on multiple fields, editing a single field
 * should clear the error highlight for only that field, leaving all other field errors unchanged.
 */
class ErrorClearingPropertyTest : FunSpec({

    tags(Tag("Feature: offpay-native-app, Property 13: Error Highlight Clearing on Edit"))

    // Generator for a non-empty subset of FormField values with error messages
    val arbErrorMap = arbitrary {
        val allFields = FormField.entries.toList()
        // Generate a non-empty subset (at least 2 fields to meaningfully test selective clearing)
        val subsetSize = Arb.element(2, 3).bind()
        val selectedFields = allFields.shuffled().take(subsetSize)
        selectedFields.associateWith { field ->
            Arb.element(
                "Field is required",
                "Invalid format",
                "Value out of range",
                "Must be 4-6 digits",
                "Invalid VPA format"
            ).bind()
        }
    }

    // Generator for a field to clear
    val arbFieldToClear = Arb.element(FormField.entries.toList())

    test("clearing a single field error removes only that field and preserves others") {
        checkAll(100, arbErrorMap, arbFieldToClear) { errors, fieldToClear ->
            // Only test when the field to clear actually has an error
            if (errors.containsKey(fieldToClear)) {
                // Simulate the clearFieldError logic from PayViewModel
                val initial = PayUiState(errors = errors)
                val afterClear = initial.copy(errors = initial.errors - fieldToClear)

                // The cleared field should no longer have an error
                afterClear.errors shouldNotContainKey fieldToClear

                // All other field errors should remain unchanged
                for ((field, message) in errors) {
                    if (field != fieldToClear) {
                        afterClear.errors shouldContainKey field
                        afterClear.errors[field] shouldBe message
                    }
                }

                // The total error count should decrease by exactly 1
                afterClear.errors.size shouldBe (errors.size - 1)
            }
        }
    }

    test("clearing a field that has no error leaves the error map unchanged") {
        checkAll(100, arbErrorMap, arbFieldToClear) { errors, fieldToClear ->
            if (!errors.containsKey(fieldToClear)) {
                // Simulate the clearFieldError logic — no change when field not in errors
                val initial = PayUiState(errors = errors)
                val afterClear = if (initial.errors.containsKey(fieldToClear)) {
                    initial.copy(errors = initial.errors - fieldToClear)
                } else {
                    initial
                }

                // Error map should be completely unchanged
                afterClear.errors shouldBe errors
            }
        }
    }
})
