package com.offpay.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.offpay.app.domain.UssdEnginePort
import com.offpay.app.domain.UssdFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Central USSD session coordinator. Manages session lifecycle, frame emission,
 * double-tap protection, and inactivity/hard timeouts.
 *
 * Implements [UssdEnginePort] for the domain layer and [UssdFrameListener] to
 * receive frames from [UssdAccessibilityService].
 */
class UssdEngine(
    private val context: Context,
    private val overlayController: OverlayController? = null
) : UssdEnginePort, UssdFrameListener {

    // ─── Session State ─────────────────────────────────────────────────────────

    /** Monotonically increasing session identifier. Incremented on each dial(). */
    @Volatile
    private var sessionId: Int = 0

    /** Whether a USSD session is currently active. */
    @Volatile
    private var sessionActive: Boolean = false

    /** Per-session frame counter for assigning frameId. */
    private var frameCounter: Int = 0

    /** Timestamp (elapsedRealtime) of the last dial() call for double-tap protection. */
    private var lastDialTime: Long = 0L

    // ─── Coroutine Infrastructure ──────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Slow_Watch job: 12s inactivity timer, resets on frame/reply activity. */
    private var slowWatchJob: Job? = null

    /** Hard_Timeout job: absolute cap per session. */
    private var hardTimeoutJob: Job? = null

    // ─── Frame Emission ────────────────────────────────────────────────────────

    private val _frames = MutableSharedFlow<UssdFrame>(extraBufferCapacity = 16)
    override val frames: SharedFlow<UssdFrame> = _frames

    // ─── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val DOUBLE_TAP_COOLDOWN_MS = 2_000L
        const val SLOW_WATCH_TIMEOUT_MS = 12_000L
        const val HARD_TIMEOUT_SEND_MS = 25_000L
        const val HARD_TIMEOUT_OTHER_MS = 30_000L
    }

    // ─── UssdEnginePort Implementation ─────────────────────────────────────────

    /**
     * Initiates a new USSD session by dialing the given code.
     *
     * Order of operations matches the proven UssdTest reference exactly:
     *  1. Double-tap guard
     *  2. dismissDialog() — swallow leftover dialog from prior run
     *  3. resetForNewSession() on the service — wipe lastEmittedText
     *  4. Cancel any in-flight timers, clear counters
     *  5. Increment sessionId
     *  6. sessionActive = true on engine and service
     *  7. Hide leftover overlay
     *  8. Register frame listener
     *  9. Start timers
     *  10. startActivity(ACTION_CALL)
     *
     * Diverging from this order breaks repeat-run reliability — the service
     * can leak a stale lastEmittedText into the next session, or the
     * sessionId guard fails to discard stale frames from the previous run.
     */
    override suspend fun dial(code: String) {
        // 1. Double-tap protection: ignore if < 2s since last dial
        val now = SystemClock.elapsedRealtime()
        if (now - lastDialTime < DOUBLE_TAP_COOLDOWN_MS) {
            return
        }
        lastDialTime = now

        val service = UssdAccessibilityService.instance

        // 2. Dismiss leftover carrier dialog from prior run
        service?.dismissDialog()

        // 3. Reset the service's dedup state for a fresh session
        service?.resetForNewSession()

        // 4. Reset internal state (cancel timers, clear counters)
        resetState()

        // 5. Increment session ID (monotonically increasing)
        sessionId++
        frameCounter = 0

        // 6. Mark session active on engine AND service (service uses this
        //    to gate accessibility-event handling).
        sessionActive = true
        service?.sessionActive = true

        // 7. Hide overlay from prior session
        overlayController?.hide()

        // 8. Register as frame listener
        service?.frameListener = this

        // 9. Start timers
        startSlowWatch()
        startHardTimeout(inferHardTimeout(code))

        // 10. Dial via ACTION_CALL intent
        val encodedCode = code.replace("#", Uri.encode("#"))
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$encodedCode")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
    }

    /**
     * Sends a reply to the active USSD carrier dialog.
     * Resets the Slow_Watch timer on successful reply.
     */
    override suspend fun sendReply(reply: String): Boolean {
        if (!sessionActive) return false

        val service = UssdAccessibilityService.instance ?: return false
        val success = service.sendReply(reply)

        if (success) {
            resetSlowWatch()
        }

        return success
    }

    /**
     * Cancels the active USSD session. Dismisses the dialog, hides overlay,
     * and resets all session state.
     */
    override suspend fun cancel() {
        if (!sessionActive) return
        terminateSession("User cancelled")
    }

    /**
     * Dismisses any leftover carrier dialog from a prior session.
     */
    override suspend fun dismissDialog(): Boolean {
        val service = UssdAccessibilityService.instance ?: return false
        return service.dismissDialog()
    }

    override fun getSessionId(): Int = sessionId

    override fun isServiceEnabled(): Boolean = UssdAccessibilityService.instance != null

    // ─── UssdFrameListener Implementation ──────────────────────────────────────

    /**
     * Called by [UssdAccessibilityService] when a new USSD frame is captured.
     */
    override fun onFrame(text: String, isMenu: Boolean, isTerminal: Boolean) {
        if (!sessionActive) return

        frameCounter++
        val frame = UssdFrame(
            text = text,
            isMenu = isMenu,
            isTerminal = isTerminal,
            sessionId = sessionId,
            frameId = frameCounter
        )

        _frames.tryEmit(frame)

        // Reset slow watch on frame activity
        resetSlowWatch()
    }

    override fun onSessionEnded(reason: String) {
        if (!sessionActive) return
        // The dialog window went away unexpectedly. Surface a terminal frame
        // so ActionRunner sees the session ended.
        frameCounter++
        _frames.tryEmit(
            UssdFrame(
                text = reason,
                isMenu = false,
                isTerminal = true,
                sessionId = sessionId,
                frameId = frameCounter
            )
        )
    }

    // ─── Timer Management ──────────────────────────────────────────────────────

    private fun startSlowWatch() {
        slowWatchJob?.cancel()
        slowWatchJob = scope.launch {
            delay(SLOW_WATCH_TIMEOUT_MS)
            if (sessionActive) {
                terminateSession("Carrier unresponsive — no activity for 12 seconds")
            }
        }
    }

    private fun resetSlowWatch() {
        if (sessionActive) {
            startSlowWatch()
        }
    }

    private fun startHardTimeout(timeoutMs: Long) {
        hardTimeoutJob?.cancel()
        hardTimeoutJob = scope.launch {
            delay(timeoutMs)
            if (sessionActive) {
                terminateSession("Session timed out after ${timeoutMs / 1000}s")
            }
        }
    }

    // ─── Session Lifecycle ─────────────────────────────────────────────────────

    private fun terminateSession(reason: String) {
        if (!sessionActive) return
        sessionActive = false
        UssdAccessibilityService.instance?.sessionActive = false

        slowWatchJob?.cancel()
        slowWatchJob = null
        hardTimeoutJob?.cancel()
        hardTimeoutJob = null

        UssdAccessibilityService.instance?.dismissDialog()
        overlayController?.hide()

        // Emit a terminal frame so ActionRunner knows the session ended.
        frameCounter++
        _frames.tryEmit(
            UssdFrame(
                text = reason,
                isMenu = false,
                isTerminal = true,
                sessionId = sessionId,
                frameId = frameCounter
            )
        )
    }

    private fun resetState() {
        sessionActive = false
        UssdAccessibilityService.instance?.sessionActive = false
        frameCounter = 0
        slowWatchJob?.cancel()
        slowWatchJob = null
        hardTimeoutJob?.cancel()
        hardTimeoutJob = null
    }

    private fun inferHardTimeout(code: String): Long {
        return if (code.contains("*99*1*3")) {
            HARD_TIMEOUT_SEND_MS
        } else {
            HARD_TIMEOUT_OTHER_MS
        }
    }
}
