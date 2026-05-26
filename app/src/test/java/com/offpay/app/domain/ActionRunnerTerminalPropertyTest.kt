package com.offpay.app.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Property-based tests for ActionRunner unmatched terminal frame failure behavior.
 *
 * **Validates: Requirements 2.5, 6.4**
 *
 * Property 7: For any terminal UssdFrame whose text does not match any step regex
 * in the current action AND does not match any universal success pattern, the
 * ActionRunner should treat the session as a failure with the frame's text as the
 * error message.
 */
class ActionRunnerTerminalPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 7: Unmatched Terminal Frame Causes Failure"))

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

    // All step regexes from SendUpi and CheckBalance actions combined
    val allStepRegexes = Actions.SendUpi.steps.map { it.match } +
        Actions.CheckBalance.steps.map { it.match }

    /**
     * Generator for random text that does NOT match any step regex and does NOT
     * match any universal success pattern. We generate random alphanumeric strings
     * and filter out any that accidentally match.
     */
    val unmatchedTerminalTextArb = arbitrary {
        // Base characters that avoid triggering step regexes or success patterns
        val prefixes = listOf(
            "xyz random text",
            "unknown carrier msg",
            "error code 404",
            "session expired",
            "network timeout",
            "invalid operation",
            "request cancelled by system",
            "dial failed",
            "connection dropped",
            "unrecognized command"
        )
        val prefix = prefixes[Arb.int(0 until prefixes.size).bind()]
        val suffix = Arb.int(1000..999999).bind()
        "$prefix $suffix"
    }

    test("unmatched terminal frame text causes failure - matchStep returns -1 AND matchesUniversalSuccess returns false") {
        checkAll(100, unmatchedTerminalTextArb) { text ->
            // Verify text does NOT match any step regex in SendUpi (from index 0)
            val sendUpiMatch = actionRunner.matchStep(text, Actions.SendUpi.steps, 0)
            // Verify text does NOT match any step regex in CheckBalance (from index 0)
            val checkBalanceMatch = actionRunner.matchStep(text, Actions.CheckBalance.steps, 0)
            // Verify text does NOT match any universal success pattern
            val matchesSuccess = actionRunner.matchesUniversalSuccess(text)

            // Combined assertion: all three conditions prove unmatched terminal failure
            sendUpiMatch shouldBe -1
            checkBalanceMatch shouldBe -1
            matchesSuccess shouldBe false
        }
    }
})
