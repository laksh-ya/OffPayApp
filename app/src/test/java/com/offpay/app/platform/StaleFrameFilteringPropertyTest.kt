package com.offpay.app.platform

import com.offpay.app.domain.Action
import com.offpay.app.domain.ActionResult
import com.offpay.app.domain.ActionRunner
import com.offpay.app.domain.ActionStep
import com.offpay.app.domain.UssdEnginePort
import com.offpay.app.domain.UssdFrame
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Property-based test for stale frame filtering.
 *
 * **Validates: Requirements 5.2**
 *
 * For any active session with sessionId S, and any UssdFrame with a sessionId ≠ S,
 * the frame should be discarded and not forwarded to the ActionRunner.
 */
class StaleFrameFilteringPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 10: Stale Frame Filtering"))

    /**
     * Fake UssdEnginePort that allows controlled frame emission and tracks replies.
     */
    class FakeUssdEngine(private val currentSessionId: Int) : UssdEnginePort {
        val frameFlow = MutableSharedFlow<UssdFrame>(extraBufferCapacity = 64)
        val repliesSent = mutableListOf<String>()
        var dialCalled = false
        var cancelCalled = false

        override suspend fun dial(code: String) { dialCalled = true }
        override suspend fun sendReply(reply: String): Boolean {
            repliesSent.add(reply)
            return true
        }
        override suspend fun cancel() { cancelCalled = true }
        override suspend fun dismissDialog(): Boolean = true
        override fun getSessionId(): Int = currentSessionId
        override fun isServiceEnabled(): Boolean = true
        override val frames: SharedFlow<UssdFrame> = frameFlow.asSharedFlow()
    }

    // A simple action with one step that matches any text containing "enter amount"
    // and expects a reply, followed by a done step.
    val testAction = Action(
        code = "*99#",
        steps = listOf(
            ActionStep(
                match = Regex("enter amount", RegexOption.IGNORE_CASE),
                reply = "{amount}",
                label = "Entering amount"
            ),
            ActionStep(
                match = Regex("successful", RegexOption.IGNORE_CASE),
                done = true,
                label = "Done"
            )
        ),
        timeoutMs = 5_000L
    )

    // Generator for session IDs that differ from the current session
    fun staleSessionIdArb(currentSessionId: Int) = arbitrary {
        var id = Arb.int(1..10000).bind()
        // Ensure it's different from currentSessionId
        if (id == currentSessionId) id += 1
        id
    }

    test("frames with sessionId != current sessionId are discarded and never trigger replies") {
        checkAll(100, Arb.int(1..10000), Arb.string(5..20)) { currentSessionId, amountStr ->
            val engine = FakeUssdEngine(currentSessionId)
            val runner = ActionRunner(engine)
            val vars = mapOf("amount" to amountStr)

            runTest {
                val scope = CoroutineScope(coroutineContext)
                val actionRun = runner.runAction(testAction, vars, scope)

                // Emit frames with stale (different) sessionIds — these should be filtered
                val staleSessionId = if (currentSessionId == Int.MAX_VALUE) currentSessionId - 1 else currentSessionId + 1
                engine.frameFlow.emit(
                    UssdFrame(
                        text = "Enter Amount",
                        isMenu = false,
                        isTerminal = false,
                        sessionId = staleSessionId,
                        frameId = 1
                    )
                )

                // Give the coroutine a moment to process
                kotlinx.coroutines.yield()

                // No reply should have been sent because the frame was stale
                engine.repliesSent.size shouldBe 0

                // Now emit a valid frame with the correct sessionId to verify processing works
                engine.frameFlow.emit(
                    UssdFrame(
                        text = "Enter Amount",
                        isMenu = false,
                        isTerminal = false,
                        sessionId = currentSessionId,
                        frameId = 2
                    )
                )

                // Wait for the action to process
                kotlinx.coroutines.delay(300)

                // The valid frame should have triggered a reply
                engine.repliesSent.size shouldBe 1
                engine.repliesSent[0] shouldBe amountStr

                // Send terminal frame to complete the action
                engine.frameFlow.emit(
                    UssdFrame(
                        text = "Payment successful",
                        isMenu = false,
                        isTerminal = true,
                        sessionId = currentSessionId,
                        frameId = 3
                    )
                )

                // Wait for completion
                val result = withTimeoutOrNull(2000L) { actionRun.result.await() }
                result?.success shouldBe true
            }
        }
    }

    test("multiple stale frames with various sessionIds are all discarded") {
        checkAll(100, Arb.int(1..10000)) { currentSessionId ->
            val engine = FakeUssdEngine(currentSessionId)
            val runner = ActionRunner(engine)
            val vars = mapOf("amount" to "100")

            runTest {
                val scope = CoroutineScope(coroutineContext)
                val actionRun = runner.runAction(testAction, vars, scope)

                // Emit multiple stale frames with various different sessionIds
                val staleIds = listOf(
                    currentSessionId + 1,
                    currentSessionId + 2,
                    currentSessionId - 1,
                    0,
                    currentSessionId + 100
                ).filter { it != currentSessionId }

                for ((index, staleId) in staleIds.withIndex()) {
                    engine.frameFlow.emit(
                        UssdFrame(
                            text = "Enter Amount",
                            isMenu = false,
                            isTerminal = false,
                            sessionId = staleId,
                            frameId = index + 1
                        )
                    )
                }

                kotlinx.coroutines.yield()

                // None of the stale frames should have triggered a reply
                engine.repliesSent.size shouldBe 0

                // Complete the session with valid frames
                engine.frameFlow.emit(
                    UssdFrame(
                        text = "Payment successful",
                        isMenu = false,
                        isTerminal = true,
                        sessionId = currentSessionId,
                        frameId = 10
                    )
                )

                val result = withTimeoutOrNull(2000L) { actionRun.result.await() }
                result?.success shouldBe true
            }
        }
    }
})
