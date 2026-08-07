package com.offpay.app.platform

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * TYPE_APPLICATION_OVERLAY window painted ABOVE the carrier USSD dialog so
 * the user only ever sees branded UI while a session runs. Mirrors the
 * proven implementation from `References/UssdTest`.
 *
 * Two modes:
 *  - **Full overlay** (`show`/`update`): covers the carrier dialog entirely.
 *  - **Minimal floating bar** (`showMinimal`/`updateMinimal`): a small bar
 *    pinned below the status bar; carrier dialog stays visible underneath.
 *
 * Critical flag on both windows: `FLAG_NOT_FOCUSABLE`. If the overlay grabs
 * keyboard focus, the underlying carrier dialog can't reliably receive
 * AccessibilityService setText/click actions.
 */
class OverlayControllerImpl(private val context: Context) : OverlayController {

    private val handler = Handler(Looper.getMainLooper())

    // ── Full overlay state ─────────────────────────────────────────────────────
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var stepLabelView: TextView? = null
    private var spinnerView: ProgressBar? = null
    private var sendButton: Button? = null
    private var cancelButton: Button? = null

    // ── Minimal floating bar state ─────────────────────────────────────────────
    private var minimalView: LinearLayout? = null
    private var minimalLabel: TextView? = null
    private var minimalStepCount: TextView? = null
    private var minimalProgressFill: View? = null
    private var minimalProgressTrack: FrameLayout? = null

    override var onCancel: (() -> Unit)? = null
    override var onConfirm: (() -> Unit)? = null
    override var onMinimalTapped: (() -> Unit)? = null

    private fun fullParams(): WindowManager.LayoutParams {
        val type = if (UssdAccessibilityService.instance != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val realHeight = getRealDisplayHeight()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            realHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun getWindowManager(): WindowManager {
        val service = UssdAccessibilityService.instance
        return if (service != null) {
            service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        } else {
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
    }

    private fun getHostContext(): Context = UssdAccessibilityService.instance ?: context

    override fun canShow(): Boolean = android.provider.Settings.canDrawOverlays(context)

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
    // ─── Full overlay ──────────────────────────────────────────────────────────

    override fun show(title: String, subtitle: String, stepLabel: String) {
        if (!canShow()) return
        // If the minimal bar is up, dismiss it before opening the full overlay.
        removeMinimalView()

        runOnMain {
            if (overlayView != null) {
                update(title, subtitle, stepLabel)
                return@runOnMain
            }
            try {
                val v = buildFullView(title, subtitle, stepLabel)
                getWindowManager().addView(v, fullParams())
                overlayView = v
                sendButton?.visibility = if (onConfirm != null) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                overlayView = null
            }
        }
    }

    override fun update(title: String, subtitle: String, stepLabel: String) {
        runOnMain {
            spinnerView?.visibility = View.VISIBLE
            titleView?.setTextColor(NEOPOP_WHITE)
            titleView?.text = title
            subtitleView?.text = subtitle
            stepLabelView?.text = stepLabel.uppercase()
            sendButton?.visibility = if (onConfirm != null) View.VISIBLE else View.GONE
            
            sendButton?.background = neoPopButtonBg(NEOPOP_ACCENT)
            sendButton?.setTextColor(NEOPOP_ACCENT)
            sendButton?.isClickable = true
        }
    }

    // ─── Minimal bar ───────────────────────────────────────────────────────────

    override fun showMinimal(progress: Int, total: Int, label: String) {
        if (!canShow()) return
        // If the full overlay is up, dismiss it before opening the minimal bar.
        removeFullView()

        runOnMain {
            if (minimalView != null) {
                updateMinimal(progress, total, label)
                return@runOnMain
            }
            try {
                val v = buildMinimalView(progress, total, label)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (UssdAccessibilityService.instance != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (48 * context.resources.displayMetrics.density).toInt()
                }
                getWindowManager().addView(v, params)
                minimalView = v
            } catch (e: Exception) {
                minimalView = null
            }
        }
    }

    override fun updateMinimal(progress: Int, total: Int, label: String) {
        runOnMain {
            minimalLabel?.text = label.uppercase() + "..."
            val safeTotal = total.coerceAtLeast(1)
            val displayedStep = (progress + 1).coerceIn(1, safeTotal)
            minimalStepCount?.text = "STEP $displayedStep / $safeTotal"
            minimalProgressTrack?.let { track ->
                track.post {
                    val fraction = (progress.toFloat() / safeTotal).coerceIn(0f, 1f)
                    val newWidth = (track.width * fraction).toInt()
                    minimalProgressFill?.layoutParams = FrameLayout.LayoutParams(newWidth, FrameLayout.LayoutParams.MATCH_PARENT)
                }
            }
        }
    }

    override fun showError(title: String, message: String, holdMs: Long) {
        runOnMain {
            // Prefer minimal bar feedback if it's already up.
            if (minimalView != null) {
                minimalLabel?.text = title.uppercase()
                minimalLabel?.setTextColor(NEOPOP_DANGER)
                minimalStepCount?.text = message.take(40)
                handler.postDelayed({ hide() }, holdMs)
                return@runOnMain
            }
            if (overlayView == null) {
                show(title, message, "")
            }
            spinnerView?.visibility = View.GONE
            titleView?.setTextColor(NEOPOP_DANGER)
            titleView?.text = title
            subtitleView?.text = message
            stepLabelView?.text = "TAP CANCEL TO DISMISS"
            handler.postDelayed({ hide() }, holdMs)
        }
    }

    override fun hide() {
        runOnMain {
            removeFullView()
            removeMinimalView()
        }
    }

    private fun removeFullView() {
        overlayView?.let { view ->
            try {
                getWindowManager().removeView(view)
            } catch (e: Exception) {}
        }
        overlayView = null
        titleView = null
        subtitleView = null
        stepLabelView = null
        spinnerView = null
        sendButton = null
        cancelButton = null
    }

    private fun removeMinimalView() {
        minimalView?.let { view ->
            try {
                getWindowManager().removeView(view)
            } catch (e: Exception) {}
        }
        minimalView = null
        minimalLabel = null
        minimalStepCount = null
        minimalProgressFill = null
        minimalProgressTrack = null
    }

    // ─── Full overlay view construction ────────────────────────────────────────
    private fun buildFullView(title: String, subtitle: String, stepLabel: String): View {
        val host = getHostContext()
        val realHeight = getRealDisplayHeight()
        val statusBarHeight = getStatusBarHeight(host)

        val root = FrameLayout(host).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, realHeight)
            setBackgroundColor(Color.TRANSPARENT)
        }

        // The Shield: Solid body that anchors to the bottom and extends UP 
        // to the status bar line. This ensures the navigation bar area 
        // at the bottom is perfectly masked.
        val shield = FrameLayout(host).apply {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, realHeight - statusBarHeight)
            lp.gravity = Gravity.BOTTOM
            layoutParams = lp
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }

        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = neoPopCardBg()
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val brand = TextView(host).apply {
            text = "OFFPAY"
            setTextColor(NEOPOP_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        spinnerView = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NEOPOP_ACCENT)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
                )
            lp.topMargin = dp(20)
            layoutParams = lp
        }

        titleView = TextView(host).apply {
            text = title
            setTextColor(NEOPOP_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            layoutParams = lp
        }

        subtitleView = TextView(host).apply {
            text = subtitle
            setTextColor(NEOPOP_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
        }

        stepLabelView = TextView(host).apply {
            text = stepLabel.uppercase()
            setTextColor(NEOPOP_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(20)
            layoutParams = lp
        }

        val send = Button(host).apply {
            text = "SEND"
            setTextColor(NEOPOP_ACCENT)
            background = neoPopButtonBg(NEOPOP_ACCENT)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isAllCaps = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                it.background = GradientDrawable().apply { setColor(NEOPOP_ACCENT); cornerRadius = dp(4).toFloat() }
                (it as Button).setTextColor(Color.BLACK)
                it.isClickable = false
                onConfirm?.invoke()
            }
        }
        sendButton = send

        val cancel = Button(host).apply {
            text = "CANCEL"
            setTextColor(NEOPOP_DANGER)
            background = neoPopButtonBg(NEOPOP_DANGER)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isAllCaps = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCancel?.invoke()
                hide()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(12) }
        }
        cancelButton = cancel

        card.addView(brand); card.addView(spinnerView); card.addView(titleView); card.addView(subtitleView); card.addView(stepLabelView)
        actions.addView(send); actions.addView(cancel); card.addView(actions)

        val cardLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(120); leftMargin = dp(20); rightMargin = dp(20)
        }
        shield.addView(card, cardLp)
        root.addView(shield)
        return root
    }

    // ─── Minimal floating bar view construction ────────────────────────────────

    private fun buildMinimalView(progress: Int, total: Int, label: String): LinearLayout {
        val host = getHostContext()
        val container = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = minimalBarBg()
            setPadding(dp(16), dp(12), dp(16), dp(10))
            // 90% screen width — the WindowManager.LayoutParams.MATCH_PARENT
            // along with horizontal margin handles this approximately.
            // Real margins are baked into params at attach time; here we just
            // pad the inner view to leave a gutter.
            isClickable = true
            setOnClickListener { onMinimalTapped?.invoke() }
        }

        // Top row: spinner + label + step count
        val topRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val spinner = ProgressBar(host).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NEOPOP_ACCENT)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(10) }
        }

        minimalLabel = TextView(host).apply {
            text = label.uppercase() + "..."
            setTextColor(NEOPOP_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        minimalStepCount = TextView(host).apply {
            text = "STEP ${progress + 1} / $total"
            setTextColor(NEOPOP_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        topRow.addView(spinner); topRow.addView(minimalLabel); topRow.addView(minimalStepCount)

        // Bottom: 2dp progress track with lime fill
        minimalProgressTrack = FrameLayout(host).apply {
            setBackgroundColor(NEOPOP_BORDER)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(10) }
        }
        minimalProgressFill = View(host).apply {
            setBackgroundColor(NEOPOP_ACCENT)
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            // Initial pixel-width is a fudge; updateMinimal() recomputes it
            // once the track has measured.
        }
        minimalProgressTrack?.addView(minimalProgressFill)

        container.addView(topRow); container.addView(minimalProgressTrack)
        return container
    }

    private fun getStatusBarHeight(c: Context): Int {
        val resourceId = c.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) c.resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun getRealDisplayHeight(): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.heightPixels
    }

    private fun minimalBarBg() = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(NEOPOP_MINIMAL_FILL); setStroke(dp(1), NEOPOP_ACCENT) }
    private fun neoPopCardBg() = GradientDrawable().apply { setColor(NEOPOP_SURFACE_HIGH); setStroke(dp(1), NEOPOP_BORDER) }
    private fun neoPopButtonBg(stroke: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { setColor(stroke.withAlpha(0.15f)); setStroke(dp(2), stroke); cornerRadius = dp(4).toFloat() })
        addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(1), stroke); cornerRadius = dp(4).toFloat() })
    }

    private fun Int.withAlpha(alpha: Float) = (this and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        // NeoPOP palette (mirrors presentation/ui/theme/Colors.kt)
        private const val NEOPOP_SURFACE_HIGH = 0xFF16181D.toInt()
        private const val NEOPOP_BORDER = 0xFF2A2D34.toInt()
        private const val NEOPOP_WHITE = 0xFFFFFFFF.toInt()
        private const val NEOPOP_TEXT_SECONDARY = 0xFF9BA1A8.toInt()
        private const val NEOPOP_ACCENT = 0xFFC5F542.toInt()
        private const val NEOPOP_DANGER = 0xFFFF4D4D.toInt()
        private const val NEOPOP_MINIMAL_FILL = 0xEB000000.toInt()
    }
}
