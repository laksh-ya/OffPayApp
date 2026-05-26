package com.offpay.app.platform

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
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

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val handler = Handler(Looper.getMainLooper())

    // ── Full overlay state ─────────────────────────────────────────────────────
    private var overlayView: FrameLayout? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var stepLabelView: TextView? = null
    private var spinnerView: ProgressBar? = null

    // ── Minimal floating bar state ─────────────────────────────────────────────
    private var minimalView: LinearLayout? = null
    private var minimalLabel: TextView? = null
    private var minimalStepCount: TextView? = null
    private var minimalProgressFill: View? = null
    private var minimalProgressTrack: FrameLayout? = null

    override var onCancel: (() -> Unit)? = null
    override var onMinimalTapped: (() -> Unit)? = null

    private fun fullParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun minimalParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
    }

    override fun canShow(): Boolean = Settings.canDrawOverlays(context)

    // ─── Full overlay ──────────────────────────────────────────────────────────

    override fun show(title: String, subtitle: String, stepLabel: String) {
        if (!canShow()) return
        // If the minimal bar is up, dismiss it before opening the full overlay.
        removeMinimalView()

        handler.post {
            if (overlayView != null) {
                update(title, subtitle, stepLabel)
                return@post
            }
            try {
                val v = buildFullView(title, subtitle, stepLabel)
                windowManager.addView(v, fullParams())
                overlayView = v
            } catch (_: Exception) {
                overlayView = null
            }
        }
    }

    override fun update(title: String, subtitle: String, stepLabel: String) {
        handler.post {
            spinnerView?.visibility = View.VISIBLE
            titleView?.setTextColor(NEOPOP_WHITE)
            titleView?.text = title
            subtitleView?.text = subtitle
            stepLabelView?.text = stepLabel.uppercase()
        }
    }

    // ─── Minimal bar ───────────────────────────────────────────────────────────

    override fun showMinimal(progress: Int, total: Int, label: String) {
        if (!canShow()) return
        // If the full overlay is up, dismiss it before opening the minimal bar.
        removeFullView()

        handler.post {
            if (minimalView != null) {
                updateMinimal(progress, total, label)
                return@post
            }
            try {
                val v = buildMinimalView(progress, total, label)
                windowManager.addView(v, minimalParams())
                minimalView = v
            } catch (_: Exception) {
                minimalView = null
            }
        }
    }

    override fun updateMinimal(progress: Int, total: Int, label: String) {
        handler.post {
            minimalLabel?.text = label.uppercase() + "…"
            minimalLabel?.setTextColor(NEOPOP_WHITE)
            val safeTotal = total.coerceAtLeast(1)
            val displayedStep = (progress + 1).coerceIn(1, safeTotal)
            minimalStepCount?.text = "STEP $displayedStep / $safeTotal"
            // Update progress fill width using weights/layout params.
            minimalProgressTrack?.let { track ->
                track.post {
                    val fraction = (progress.toFloat() / safeTotal).coerceIn(0f, 1f)
                    val newWidth = (track.width * fraction).toInt()
                    minimalProgressFill?.layoutParams =
                        FrameLayout.LayoutParams(
                            newWidth,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                }
            }
        }
    }

    override fun showError(title: String, message: String, holdMs: Long) {
        handler.post {
            // Prefer minimal bar feedback if it's already up.
            if (minimalView != null) {
                minimalLabel?.text = title.uppercase()
                minimalLabel?.setTextColor(NEOPOP_DANGER)
                minimalStepCount?.text = message.take(40)
                handler.postDelayed({ hide() }, holdMs)
                return@post
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
        handler.post {
            removeFullView()
            removeMinimalView()
        }
    }

    private fun removeFullView() {
        overlayView?.let { view ->
            try {
                if (view.parent != null) windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        titleView = null
        subtitleView = null
        stepLabelView = null
        spinnerView = null
    }

    private fun removeMinimalView() {
        minimalView?.let { view ->
            try {
                if (view.parent != null) windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        minimalView = null
        minimalLabel = null
        minimalStepCount = null
        minimalProgressFill = null
        minimalProgressTrack = null
    }

    // ─── Full overlay view construction ────────────────────────────────────────

    private fun buildFullView(title: String, subtitle: String, stepLabel: String): FrameLayout {
        val dim = FrameLayout(context).apply {
            setBackgroundColor(NEOPOP_DIM)
            isClickable = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = neoPopCardBg()
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val brand = TextView(context).apply {
            text = "OFFPAY"
            setTextColor(NEOPOP_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        spinnerView = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NEOPOP_ACCENT)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(20)
            layoutParams = lp
        }

        titleView = TextView(context).apply {
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

        subtitleView = TextView(context).apply {
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

        stepLabelView = TextView(context).apply {
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

        val cancel = Button(context).apply {
            text = "CANCEL"
            setTextColor(NEOPOP_DANGER)
            background = neoPopButtonBg(NEOPOP_DANGER)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isAllCaps = true
            letterSpacing = 0.1f
            setOnClickListener {
                onCancel?.invoke()
                hide()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.END
            lp.topMargin = dp(20)
            layoutParams = lp
        }

        card.addView(brand)
        card.addView(spinnerView)
        card.addView(titleView)
        card.addView(subtitleView)
        card.addView(stepLabelView)
        card.addView(cancel)

        val cardLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(80)
            leftMargin = dp(20)
            rightMargin = dp(20)
        }
        dim.addView(card, cardLp)
        return dim
    }

    // ─── Minimal floating bar view construction ────────────────────────────────

    private fun buildMinimalView(progress: Int, total: Int, label: String): LinearLayout {
        val safeTotal = total.coerceAtLeast(1)
        val displayedStep = (progress + 1).coerceIn(1, safeTotal)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = minimalBarBg()
            setPadding(dp(16), dp(12), dp(16), dp(0))
            // 90% screen width — the WindowManager.LayoutParams.MATCH_PARENT
            // along with horizontal margin handles this approximately.
            val sideMargin = dp(20)
            val lp = WindowManager.LayoutParams()
            // Real margins are baked into params at attach time; here we just
            // pad the inner view to leave a gutter.
            setPadding(dp(16) + sideMargin, dp(12), dp(16) + sideMargin, dp(0))
            isClickable = true
            setOnClickListener {
                onMinimalTapped?.invoke()
            }
        }

        // Top row: spinner + label + step count
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        val spinner = ProgressBar(context).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NEOPOP_ACCENT)
            val lp = LinearLayout.LayoutParams(dp(18), dp(18))
            lp.rightMargin = dp(10)
            layoutParams = lp
        }

        minimalLabel = TextView(context).apply {
            text = label.uppercase() + "…"
            setTextColor(NEOPOP_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = lp
        }

        minimalStepCount = TextView(context).apply {
            text = "STEP $displayedStep / $safeTotal"
            setTextColor(NEOPOP_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.1f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        topRow.addView(spinner)
        topRow.addView(minimalLabel)
        topRow.addView(minimalStepCount)

        // Bottom: 2dp progress track with lime fill
        minimalProgressTrack = FrameLayout(context).apply {
            setBackgroundColor(NEOPOP_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            )
            lp.topMargin = dp(10)
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }
        minimalProgressFill = View(context).apply {
            setBackgroundColor(NEOPOP_ACCENT)
            val fraction = (progress.toFloat() / safeTotal).coerceIn(0f, 1f)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Initial pixel-width is a fudge; updateMinimal() recomputes it
            // once the track has measured.
            lp.width = 0
            layoutParams = lp
        }
        minimalProgressTrack?.addView(minimalProgressFill)

        container.addView(topRow)
        container.addView(minimalProgressTrack)
        return container
    }

    private fun minimalBarBg(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(NEOPOP_MINIMAL_FILL)
            setStroke(dp(1), NEOPOP_ACCENT)
        }
    }

    private fun neoPopCardBg(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(NEOPOP_SURFACE_HIGH)
            setStroke(dp(1), NEOPOP_BORDER)
        }
    }

    private fun neoPopButtonBg(stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        // NeoPOP palette (mirrors presentation/ui/theme/Colors.kt)
        private const val NEOPOP_DIM = 0xCC000000.toInt()
        private const val NEOPOP_SURFACE_HIGH = 0xFF16181D.toInt()
        private const val NEOPOP_BORDER = 0xFF2A2D34.toInt()
        private const val NEOPOP_WHITE = 0xFFFFFFFF.toInt()
        private const val NEOPOP_TEXT_SECONDARY = 0xFF9BA1A8.toInt()
        private const val NEOPOP_ACCENT = 0xFFC5F542.toInt()
        private const val NEOPOP_DANGER = 0xFFFF4D4D.toInt()
        private const val NEOPOP_MINIMAL_FILL = 0xEB000000.toInt() // ~92% opaque black
    }
}
