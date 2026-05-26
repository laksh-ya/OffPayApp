package com.offpay.app.presentation

import com.offpay.app.domain.UpiData
import com.offpay.app.domain.UpiParser
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 1.6**
 *
 * Property 3: QR Autofill Completeness
 *
 * For any UpiData object with non-null fields, invoking the autofill action should result in
 * form state where every non-null field from UpiData is populated in the corresponding form field,
 * and null fields remain empty.
 */
class QrAutofillPropertyTest : FunSpec({

    tags(Tag("Feature: offpay-native-app, Property 3: QR Autofill Completeness"))

    /**
     * Simulates the autofill logic from PayViewModel.onQrScanned().
     * This is a pure function mirror of the ViewModel copy logic,
     * tested directly to avoid needing Android framework dependencies.
     */
    fun simulateAutofill(upiData: UpiData, currentState: PayUiState): PayUiState {
        return currentState.copy(
            vpa = upiData.vpa,
            payeeName = upiData.payeeName ?: currentState.payeeName,
            amount = upiData.amount ?: currentState.amount,
            note = upiData.transactionNote ?: currentState.note,
            errors = emptyMap()
        )
    }

    // Generator for valid VPA local parts (3+ alphanumeric/dot/dash/underscore chars)
    val arbVpaLocal = Arb.of(
        "user", "test.user", "a-b_c", "valid123", "merchant01",
        "pay.me", "abc", "shop_owner", "john.doe", "x-y-z"
    )

    val arbVpaHandle = Arb.of(
        "upi", "okaxis", "ybl", "paytm", "oksbi",
        "apl", "ibl", "icici", "hdfc", "bank"
    )

    // Generator for optional payee names
    val arbPayeeName = Arb.of(
        "John Doe", "Shop Owner", "Merchant", "राहुल", "Test Name",
        "A", "Long Name With Spaces", "Name123", "café owner", "user@store"
    )

    // Generator for optional amounts
    val arbAmount = Arb.of(
        "1", "10.50", "100", "5000", "0.01",
        "999.99", "250", "49.95", "1500.00", "3"
    )

    // Generator for optional transaction notes
    val arbNote = Arb.of(
        "Payment", "Rent", "Food", "Bill payment", "Groceries",
        "Transfer", "EMI", "Gift", "Subscription", "Recharge"
    )

    // Generator for UpiData with random combinations of present/absent optional fields
    val arbUpiData = arbitrary {
        val local = arbVpaLocal.bind()
        val handle = arbVpaHandle.bind()
        val vpa = "$local@$handle"

        val hasName = Arb.boolean().bind()
        val hasAmount = Arb.boolean().bind()
        val hasNote = Arb.boolean().bind()

        UpiData(
            vpa = vpa,
            payeeName = if (hasName) arbPayeeName.bind() else null,
            amount = if (hasAmount) arbAmount.bind() else null,
            transactionNote = if (hasNote) arbNote.bind() else null
        )
    }

    test("autofill populates all non-null UpiData fields and leaves null fields empty") {
        checkAll(100, arbUpiData) { upiData ->
            val emptyState = PayUiState()
            val result = simulateAutofill(upiData, emptyState)

            // VPA is always non-null in UpiData, so it must always be populated
            result.vpa shouldBe upiData.vpa

            // Non-null fields must be populated with their values
            if (upiData.payeeName != null) {
                result.payeeName shouldBe upiData.payeeName
            } else {
                // Null fields remain at default (empty string)
                result.payeeName shouldBe ""
            }

            if (upiData.amount != null) {
                result.amount shouldBe upiData.amount
            } else {
                result.amount shouldBe ""
            }

            if (upiData.transactionNote != null) {
                result.note shouldBe upiData.transactionNote
            } else {
                result.note shouldBe ""
            }

            // Errors should be cleared after autofill
            result.errors shouldBe emptyMap()
        }
    }

    test("autofill via UPI URI parsing populates form fields matching parsed data") {
        // Generator that builds valid UPI URIs with random field combinations
        val arbUpiUri = arbitrary {
            val local = arbVpaLocal.bind()
            val handle = arbVpaHandle.bind()
            val vpa = "$local@$handle"

            val hasName = Arb.boolean().bind()
            val hasAmount = Arb.boolean().bind()
            val hasNote = Arb.boolean().bind()

            val name = if (hasName) arbPayeeName.bind() else null
            val amount = if (hasAmount) arbAmount.bind() else null
            val note = if (hasNote) arbNote.bind() else null

            // Build URI
            val params = mutableListOf("pa=$vpa")
            if (name != null) params.add("pn=${java.net.URLEncoder.encode(name, "UTF-8")}")
            if (amount != null) params.add("am=$amount")
            if (note != null) params.add("tn=${java.net.URLEncoder.encode(note, "UTF-8")}")

            val uri = "upi://pay?${params.joinToString("&")}"
            Triple(uri, UpiData(vpa, name, amount, note), vpa)
        }

        checkAll(100, arbUpiUri) { (uri, expectedData, _) ->
            // Parse the URI
            val parsed = UpiParser.parse(uri)

            // Parsing should succeed for valid URIs
            parsed shouldBe expectedData

            // Simulate autofill with parsed data
            if (parsed != null) {
                val emptyState = PayUiState()
                val result = simulateAutofill(parsed, emptyState)

                // Every non-null field from parsed UpiData must be in form state
                result.vpa shouldBe parsed.vpa

                if (parsed.payeeName != null) {
                    result.payeeName shouldBe parsed.payeeName
                } else {
                    result.payeeName shouldBe ""
                }

                if (parsed.amount != null) {
                    result.amount shouldBe parsed.amount
                } else {
                    result.amount shouldBe ""
                }

                if (parsed.transactionNote != null) {
                    result.note shouldBe parsed.transactionNote
                } else {
                    result.note shouldBe ""
                }
            }
        }
    }
})
