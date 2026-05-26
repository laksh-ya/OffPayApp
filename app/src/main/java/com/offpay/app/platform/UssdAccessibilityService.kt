package com.offpay.app.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.offpay.app.domain.FrameFilter

/**
 * Listener interface for USSD frame events.
 * The UssdEngine registers itself to receive frames from this service.
 */
interface UssdFrameListener {
    /** Called when a new USSD frame is captured. */
    fun onFrame(text: String, isMenu: Boolean, isTerminal: Boolean)

    /** Called when the OS reports the carrier dialog window has gone away. */
    fun onSessionEnded(reason: String) {}
}

/**
 * AccessibilityService that watches known USSD dialog packages,
 * extracts visible text, classifies frames as menu or terminal,
 * and provides methods to drive the carrier dialog programmatically.
 *
 * Mirrors the proven implementation from `References/UssdTest`. Critical
 * differences from a naive port:
 *  - Uses `event.source` as the primary tree (works even when our overlay
 *    is on top — `rootInActiveWindow` would point at the overlay).
 *  - Handles `TYPE_VIEW_TEXT_CHANGED` and `TYPE_WINDOWS_CHANGED`. The
 *    latter is debounced (500ms) so we can detect when the carrier dialog
 *    truly goes away (session end).
 *  - `findUssdRoot()` walks `getWindows()` so dialog lookups succeed
 *    while the branded overlay is on top.
 *  - `sendReply()` clears `lastEmittedText` so the very next frame is
 *    delivered to the listener even if its text duplicates what was last
 *    shown.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdA11y"

        /** Debounce window before deciding the carrier dialog is gone. */
        private const val SESSION_END_DEBOUNCE_MS = 500L

        @Volatile
        var instance: UssdAccessibilityService? = null
            private set

        val USSD_PACKAGES = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.app.telephonyui",
            "com.google.android.dialer",
            "com.android.dialer"
        )
    }

    private val frameFilter = FrameFilter()
    private var lastEmittedText: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val sessionEndCheck = Runnable { verifySessionStillAlive() }

    @Volatile
    var frameListener: UssdFrameListener? = null

    @Volatile
    var sessionActive: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }
        Log.d(TAG, "service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(sessionEndCheck)
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Only act while a session is running — saves CPU and avoids leaking
        // accidental dialog reads back to the listener.
        if (!sessionActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                main.removeCallbacks(sessionEndCheck)
                main.postDelayed(sessionEndCheck, SESSION_END_DEBOUNCE_MS)
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg !in USSD_PACKAGES) return

                // event.source is the dialog node tree even when the overlay
                // is on top; rootInActiveWindow would point at the overlay.
                val root: AccessibilityNodeInfo =
                    event.source ?: findUssdRoot() ?: return
                if (root.packageName?.toString() !in USSD_PACKAGES) return
                handleDialog(root)
            }
        }
    }

    override fun onInterrupt() {
        // No-op; required by AccessibilityService
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a reply to the active USSD dialog by setting text in the EditText
     * and clicking the Send/OK button.
     *
     * @return true if the reply was successfully sent
     */
    fun sendReply(reply: String): Boolean {
        val root = findUssdRoot() ?: run {
            Log.w(TAG, "sendReply: no USSD window found")
            return false
        }
        val edit = findEditText(root) ?: run {
            Log.w(TAG, "sendReply: no EditText in USSD window")
            return false
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                reply
            )
        }
        edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        // Clear the dedup state so the very next frame is re-emitted to the
        // listener even if its text duplicates what was last shown. Without
        // this, repeats of the same prompt are silently dropped.
        lastEmittedText = null
        frameFilter.reset()

        val sendBtn = findClickableButton(root, SEND_LABELS)
        val clicked = sendBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (!clicked) {
            // Some OEMs auto-submit when the EditText itself is clicked
            // (the IME action triggers a hidden Send button).
            edit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return true
    }

    /**
     * Dismisses the current USSD dialog by clicking any close-style button
     * (ok/cancel/close/dismiss/done).
     *
     * @return true if the dialog was dismissed
     */
    fun dismissDialog(): Boolean {
        val root = findUssdRoot() ?: return false
        val close = findClickableButton(root, DISMISS_LABELS) ?: return false
        val ok = close.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        lastEmittedText = null
        frameFilter.reset()
        return ok
    }

    fun cancelDialog(): Boolean = dismissDialog()

    /**
     * Resets state for a new USSD session.
     * Clears the deduplication filter and last emitted text.
     */
    fun resetForNewSession() {
        lastEmittedText = null
        frameFilter.reset()
        main.removeCallbacks(sessionEndCheck)
    }

    // ─── Internal ──────────────────────────────────────────────────────────────

    private fun handleDialog(root: AccessibilityNodeInfo) {
        val texts = collectTexts(root)
        if (texts.isEmpty()) return

        val joinedText = texts.joinToString("\n").trim()
        if (joinedText.isBlank()) return

        // Filter system placeholders
        if (frameFilter.isSystemPlaceholder(joinedText)) return

        // Dedup: don't re-emit identical frames
        if (joinedText == lastEmittedText) return

        // Classify the frame
        val isMenu = hasInput(root)
        val isTerminal = !isMenu && hasOnlyDismiss(root)

        lastEmittedText = joinedText
        Log.d(TAG, "frame: $joinedText (menu=$isMenu terminal=$isTerminal)")
        frameListener?.onFrame(joinedText, isMenu, isTerminal)
    }

    /**
     * Find the currently-visible USSD dialog's root node. Walks all windows
     * rather than relying on rootInActiveWindow, since our own overlay window
     * can be "on top" once we show it.
     */
    private fun findUssdRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { active ->
            if (active.packageName?.toString() in USSD_PACKAGES) return active
        }
        for (w: AccessibilityWindowInfo in windows.orEmpty()) {
            val r = w.root ?: continue
            if (r.packageName?.toString() in USSD_PACKAGES) return r
        }
        return null
    }

    private fun verifySessionStillAlive() {
        if (!sessionActive) return
        if (findUssdRoot() == null) {
            Log.d(TAG, "USSD window gone — session ended")
            frameListener?.onSessionEnded("dialog_dismissed")
            lastEmittedText = null
            frameFilter.reset()
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        walk(node) { n ->
            val cls = n.className?.toString().orEmpty()
            // Skip Buttons and EditTexts — they're affordances, not messages.
            if (cls.contains("Button", true) || cls.contains("EditText", true)) return@walk
            n.text?.toString()?.takeIf { it.isNotBlank() && !frameFilter.isSystemPlaceholder(it) }
                ?.let { out.add(it) }
        }
        return out
    }

    private fun hasInput(node: AccessibilityNodeInfo): Boolean {
        var found = false
        walk(node) { n ->
            if (n.className?.toString()?.contains("EditText", true) == true) found = true
        }
        return found
    }

    private fun hasOnlyDismiss(node: AccessibilityNodeInfo): Boolean {
        val labels = mutableListOf<String>()
        walk(node) { n ->
            if (n.className?.toString()?.contains("Button", true) == true) {
                n.text?.toString()?.let { labels.add(it.lowercase()) }
            }
        }
        if (labels.isEmpty()) return false
        return labels.all { l ->
            l.contains("ok") || l.contains("cancel") || l.contains("close") ||
                l.contains("dismiss") || l.contains("done")
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(node) { n ->
            if (found == null && n.className?.toString()?.contains("EditText", true) == true) {
                found = n
            }
        }
        return found
    }

    private fun findClickableButton(
        node: AccessibilityNodeInfo,
        labels: Set<String>
    ): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(node) { n ->
            if (found != null) return@walk
            val text = n.text?.toString()?.lowercase().orEmpty()
            val cls = n.className?.toString().orEmpty()
            if (cls.contains("Button", true) && labels.any { text.contains(it) }) {
                found = n
            }
        }
        return found
    }

    /** Depth-first walk over the node tree. */
    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, visit) }
        }
    }
}

private val SEND_LABELS = setOf("send", "ok", "submit", "reply")
private val DISMISS_LABELS = setOf("ok", "cancel", "close", "dismiss", "done")
