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
 * Why this approach over activity-hopping:
 *  - Activities race the dialog draw → visible flicker.
 *  - System overlays sit in their own WindowManager layer drawn after the
 *    dialog, so we never lose the first frame.
 *  - We can `update()` the overlay text in place without recreating it.
 *
 * Critical flag: `FLAG_NOT_FOCUSABLE`. If the overlay grabs keyboard focus,
 * the underlying carrier dialog can't reliably receive AccessibilityService
 * setText/click actions. Buttons inside this overlay still receive touch
 * events because touch and keyboard focus are separate concerns.
 */
class OverlayControllerImpl(private val context: Context) : OverlayController {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: FrameLayout? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var stepLabelView: TextView? = null
    private var spinnerView: ProgressBar? = null

    override var onCancel: (() -> Unit)? = null

    private fun layoutParams(): WindowManager.LayoutParams {
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
            // Reference flag set, exact:
            //   FLAG_NOT_FOCUSABLE — never steal keyboard focus from the dialog
            //   FLAG_LAYOUT_IN_SCREEN / FLAG_LAYOUT_NO_LIMITS — fill the entire screen
            //   FLAG_HARDWARE_ACCELERATED — smooth update() refreshes
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    override fun canShow(): Boolean = Settings.canDrawOverlays(context)

    override fun show(title: String, subtitle: String, stepLabel: String) {
        if (!canShow()) return

        handler.post {
            if (overlayView != null) {
                update(title, subtitle, stepLabel)
                return@post
            }
            try {
                val v = buildView(title, subtitle, stepLabel)
                windowManager.addView(v, layoutParams())
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

    override fun showError(title: String, message: String, holdMs: Long) {
        handler.post {
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
    }

    // ─── View construction (NeoPOP styled) ─────────────────────────────────────

    private fun buildView(title: String, subtitle: String, stepLabel: String): FrameLayout {
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
    }
}
