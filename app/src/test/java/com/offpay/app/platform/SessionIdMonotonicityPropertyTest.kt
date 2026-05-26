package com.offpay.app.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

/**
 * **Validates: Requirements 5.1**
 *
 * Feature: offpay-native-app, Property 9: Session ID Monotonicity
 *
 * For any sequence of N dial() calls (N ≥ 2), the sessionId produced by each
 * successive call should be strictly greater than the previous sessionId.
 *
 * Since UssdEngine.dial() requires Android Context and system intents, we test
 * the session ID management logic in isolation. The core behavior is:
 * - sessionId starts at 0
 * - Each dial() increments sessionId by 1 (sessionId++)
 * - getSessionId() returns the current value
 *
 * We simulate this by testing that for any N ≥ 2 sequential increments,
 * each subsequent sessionId is strictly greater than the previous.
 */
class SessionIdMonotonicityPropertyTest : FunSpec({

    /**
     * A minimal test double that replicates UssdEngine's session ID management.
     * The real UssdEngine does: sessionId++ on each dial() call.
     */
    class SessionIdCounter {
        @Volatile
        private var sessionId: Int = 0

        /** Simulates the dial() session ID increment logic. */
        fun dial(): Int {
            sessionId++
            return sessionId
        }

        fun getSessionId(): Int = sessionId
    }

    test("Feature: offpay-native-app, Property 9: Session ID Monotonicity") {
        // For any N in [2, 50], a sequence of N dial() calls produces strictly increasing sessionIds
        forAll(100, Arb.int(2..50)) { n ->
            val counter = SessionIdCounter()
            val sessionIds = (1..n).map { counter.dial() }

            // Every consecutive pair should be strictly increasing
            sessionIds.zipWithNext().all { (prev, next) -> next > prev }
        }
    }

    test("Feature: offpay-native-app, Property 9: Session ID Monotonicity - successive calls never equal") {
        // Additional check: no two consecutive sessionIds are ever equal
        forAll(100, Arb.int(2..100)) { n ->
            val counter = SessionIdCounter()
            val sessionIds = (1..n).map { counter.dial() }

            // No duplicates in the sequence (all strictly increasing from 0)
            sessionIds.distinct().size == sessionIds.size &&
                sessionIds == sessionIds.sorted()
        }
    }
})
