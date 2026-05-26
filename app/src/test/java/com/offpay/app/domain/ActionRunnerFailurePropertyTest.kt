package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Property-based tests for ActionRunner failure pattern detection.
 *
 * **Validates: Requirements 2.4, 3.4**
 */
class ActionRunnerFailurePropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 6: Failure Pattern Detection"))

    // Fake UssdEnginePort for creating ActionRunner instance
    val fakeEngine = object : UssdEnginePort {
        override suspend fun dial(code: String) {}
        override suspend fun sendReply(reply: String): Boolean = true
        override suspend fun cancel() {}
        override suspend fun dismissDialog(): Boolean = true
        override fun getSessionId(): Int = 1
        override fun isServiceEnabled(): Boolean = true
        override val frames: SharedFlow<UssdFrame> = MutableSharedFlow()
    }

    val actionRunner = ActionRunner(fakeEngine)

    // Failure pattern categories with example texts that MUST match
    val failureTextGenerators: List<Pair<String, List<String>>> = listOf(
        "wrong PIN" to listOf(
            "wrong pin entered",
            "Incorrect PIN. Please try again",
            "PIN wrong, transaction cancelled",
            "Your pin is incorrect"
        ),
        "invalid VPA" to listOf(
            "Invalid VPA entered",
            "VPA is invalid, please check",
            "Invalid UPI ID provided"
        ),
        "insufficient balance" to listOf(
            "Insufficient balance in your account",
            "Balance insufficient for this transaction",
            "Low balance. Cannot proceed"
        ),
        "service unavailable" to listOf(
            "Service unavailable. Please try later",
            "Temporarily unavailable",
            "Please try later",
            "Technical error occurred"
        ),
        "sender-receiver same" to listOf(
            "Sender and receiver are same",
            "Same account transfer not allowed",
            "Cannot pay yourself"
        ),
        "PSP not registered" to listOf(
            "PSP is not registered",
            "Not registered PSP. Contact your bank"
        )
    )

    // Generator that produces a string matching one of the failure patterns
    val failureTextArb = arbitrary {
        val category = Arb.element(failureTextGenerators).bind()
        val baseText = Arb.element(category.second).bind()
        // Optionally wrap with random prefix/suffix to test containsMatchIn
        val prefix = Arb.string(0..20).bind()
        val suffix = Arb.string(0..20).bind()
        "$prefix $baseText $suffix"
    }

    // Generator for strings that should NOT match any failure pattern
    val nonFailureTextArb = arbitrary {
        val safeTexts = listOf(
            "Enter UPI ID",
            "Enter Amount",
            "Enter UPI PIN",
            "Payment successful reference no 123456",
            "Balance: Rs 1000.50",
            "Select your bank",
            "Confirm payment of Rs 500?",
            "Press 1 to confirm",
            "Your request has been processed",
            "Welcome to NPCI UPI service",
            "Enter remark for transaction",
            "Transaction ID: 987654321"
        )
        val base = Arb.element(safeTexts).bind()
        val noise = Arb.string(0..10).bind()
        "$base $noise"
    }

    test("strings matching failure patterns are detected as failures") {
        checkAll(100, failureTextArb) { text ->
            actionRunner.matchesFailurePattern(text, Actions.COMMON_FAILURES) shouldBe true
        }
    }

    test("strings not matching any failure pattern return false") {
        checkAll(100, nonFailureTextArb) { text ->
            actionRunner.matchesFailurePattern(text, Actions.COMMON_FAILURES) shouldBe false
        }
    }
})
