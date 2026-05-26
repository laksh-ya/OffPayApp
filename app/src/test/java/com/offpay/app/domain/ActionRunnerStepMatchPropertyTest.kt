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
 * Property-based tests for ActionRunner step matching correctness.
 *
 * **Validates: Requirements 2.2, 3.2, 3.3**
 */
class ActionRunnerStepMatchPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 4: Action Step Matching Correctness"))

    // A fake UssdEnginePort — only needed for ActionRunner construction;
    // tests call matchStep() and fillTemplate() directly.
    val fakeEngine = object : UssdEnginePort {
        override suspend fun dial(code: String) {}
        override suspend fun sendReply(reply: String) = true
        override suspend fun cancel() {}
        override suspend fun dismissDialog() = true
        override fun getSessionId() = 1
        override fun isServiceEnabled() = true
        override val frames: SharedFlow<UssdFrame> = MutableSharedFlow()
    }

    val runner = ActionRunner(fakeEngine)

    // --- Generators ---

    // Frame texts that match each SendUpi step's regex
    val sendUpiStepFrameGenerators = listOf(
        // Step 0: VPA prompt
        Arb.element(
            "Enter receiver VPA",
            "Enter payee UPI ID",
            "recipient virtual payment address",
            "Enter the UPI ID of the receiver",
            "Enter VPA"
        ),
        // Step 1: Amount prompt
        Arb.element(
            "Enter amount",
            "Please enter the amount to pay",
            "Amount:",
            "Enter the amount"
        ),
        // Step 2: Remark prompt
        Arb.element(
            "Enter remark",
            "Add a comment",
            "Enter note for transaction",
            "Remark (optional)"
        ),
        // Step 3: PIN prompt
        Arb.element(
            "Enter UPI PIN",
            "Enter your 6 digit PIN",
            "Enter UPI pin to authorize",
            "Please enter pin"
        ),
        // Step 4: Confirm prompt
        Arb.element(
            "Press 1 to confirm",
            "Are you sure you want to pay?",
            "Confirm the transaction",
            "Press 1 to confirm payment"
        ),
        // Step 5: Success (done step)
        Arb.element(
            "Payment successful",
            "Payment sent to user@bank",
            "Payment completed. Reference no: 123456789",
            "Thank you for using *99#"
        )
    )

    // Frame texts that match CheckBalance steps
    val checkBalanceStepFrameGenerators = listOf(
        // Step 0: PIN prompt
        Arb.element(
            "Enter UPI PIN",
            "Enter your 6 digit pin",
            "Enter UPI pin",
            "Please enter pin"
        ),
        // Step 1: Balance display (done step)
        Arb.element(
            "Available balance: Rs 5000",
            "Your balance is Rs. 1234.56",
            "A/c Bal: INR 10000",
            "Ledger balance: ₹ 999"
        )
    )

    // Variable map generator
    val varsArb = arbitrary {
        val vpa = Arb.element("user@bank", "merchant@upi", "test.user@ybl", "shop-99@paytm").bind()
        val amount = Arb.element("100", "500.50", "1", "4999.99").bind()
        val note = Arb.element("payment", "rent", "food", "transfer").bind()
        val pin = Arb.element("123456", "654321", "111111", "999999").bind()
        mapOf("vpa" to vpa, "amount" to amount, "note" to note, "pin" to pin)
    }

    // --- Tests ---

    test("matchStep progresses sequentially through all SendUpi steps with matching frames") {
        val action = Actions.SendUpi

        // Generator: for each step, pick a matching frame text
        val frameSequenceArb = arbitrary {
            action.steps.mapIndexed { index, _ ->
                sendUpiStepFrameGenerators[index].bind()
            }
        }

        checkAll(100, frameSequenceArb) { frameTexts ->
            var fromIndex = 0
            for ((expectedIndex, frameText) in frameTexts.withIndex()) {
                val matchedIndex = runner.matchStep(frameText, action.steps, fromIndex)
                matchedIndex shouldBe expectedIndex
                fromIndex = matchedIndex + 1
            }
        }
    }

    test("matchStep progresses sequentially through all CheckBalance steps with matching frames") {
        val action = Actions.CheckBalance

        val frameSequenceArb = arbitrary {
            action.steps.mapIndexed { index, _ ->
                checkBalanceStepFrameGenerators[index].bind()
            }
        }

        checkAll(100, frameSequenceArb) { frameTexts ->
            var fromIndex = 0
            for ((expectedIndex, frameText) in frameTexts.withIndex()) {
                val matchedIndex = runner.matchStep(frameText, action.steps, fromIndex)
                matchedIndex shouldBe expectedIndex
                fromIndex = matchedIndex + 1
            }
        }
    }

    test("fillTemplate produces correct output with given vars for each step reply") {
        val action = Actions.SendUpi

        checkAll(100, varsArb) { vars ->
            for (step in action.steps) {
                if (step.reply != null) {
                    val filled = runner.fillTemplate(step.reply, vars)
                    // The filled template should not contain any {key} for keys present in vars
                    for ((key, value) in vars) {
                        if (step.reply.contains("{$key}")) {
                            filled.contains(value) shouldBe true
                            filled.contains("{$key}") shouldBe false
                        }
                    }
                }
            }
        }
    }

    test("matchStep with incrementing fromIndex and fillTemplate produce correct sequence end-to-end") {
        val action = Actions.SendUpi

        val combinedArb = arbitrary {
            val frames = action.steps.mapIndexed { index, _ ->
                sendUpiStepFrameGenerators[index].bind()
            }
            val vars = varsArb.bind()
            Pair(frames, vars)
        }

        checkAll(100, combinedArb) { (frameTexts, vars) ->
            var fromIndex = 0
            for ((expectedIndex, frameText) in frameTexts.withIndex()) {
                val matchedIndex = runner.matchStep(frameText, action.steps, fromIndex)
                matchedIndex shouldBe expectedIndex

                val step = action.steps[matchedIndex]
                if (step.reply != null) {
                    val reply = runner.fillTemplate(step.reply, vars)
                    // Verify template was fully resolved for known keys
                    when (step.reply) {
                        "{vpa}" -> reply shouldBe vars["vpa"]
                        "{amount}" -> reply shouldBe vars["amount"]
                        "{note}" -> reply shouldBe vars["note"]
                        "{pin}" -> reply shouldBe vars["pin"]
                        "1" -> reply shouldBe "1"
                    }
                }

                fromIndex = matchedIndex + 1
            }
            // All steps should have been matched
            fromIndex shouldBe action.steps.size
        }
    }
})
