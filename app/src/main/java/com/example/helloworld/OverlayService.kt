package com.example.helloworld

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_OVERLAY_WIDTH_DP = 120
        private const val MAX_OVERLAY_WIDTH_DP = 360
        private const val MIN_OVERLAY_HEIGHT_DP = 50
        private const val MAX_OVERLAY_HEIGHT_DP = 150
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statusDot: View? = null
    private var feedLabel: TextView? = null
    private var dotAnimator: ObjectAnimator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlayView()
        observeSettings()
    }

    override fun onDestroy() {
        dotAnimator?.cancel()
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Overlay service notification" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)

        return builder
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    // ── Overlay View ──────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    private fun createOverlayView() {
        val settings = SettingsState.state.value

        // Root container
        val container = FrameLayout(this)

        // Background card
        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(tintColorFromHue(settings.windowTintHue, settings.windowTransparency))
            setStroke(dpToPx(1), Color.argb(80, 255, 255, 255))
        }
        container.background = bg

        // Inner layout: dot + text
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        // Pulsing red dot
        val dot = View(this).apply {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            background = dotBg
        }
        val dotSize = dpToPx(10)
        val dotParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
            marginEnd = dpToPx(8)
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = dot

        // Label
        val label = TextView(this).apply {
            text = "Live Feed: Online"
            setTextColor(colorToInt(settings.fontColor))
            textSize = 14f
            typeface = getTypefaceForStyle(settings.fontStyle)
        }
        feedLabel = label

        innerLayout.addView(dot, dotParams)
        innerLayout.addView(label)
        container.addView(innerLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Window params
        val widthDp = lerp(MIN_OVERLAY_WIDTH_DP, MAX_OVERLAY_WIDTH_DP, settings.windowSizeFraction)
        val heightDp = lerp(MIN_OVERLAY_HEIGHT_DP, MAX_OVERLAY_HEIGHT_DP, settings.windowSizeFraction)

        val params = WindowManager.LayoutParams(
            dpToPx(widthDp),
            dpToPx(heightDp),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(40)
        }
        layoutParams = params

        // Touch listener for drag
        container.setOnTouchListener(DragTouchListener(params, windowManager))

        overlayView = container
        windowManager.addView(container, params)

        // Start pulsing animation
        startPulse()
    }

    private fun startPulse() {
        statusDot?.let { dot ->
            dotAnimator = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.2f).apply {
                duration = 800
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    // ── Real-time Settings Observer ───────────────────────────────

    private fun observeSettings() {
        serviceScope.launch {
            SettingsState.state.collect { s ->
                overlayView ?: return@collect
                val container = overlayView as FrameLayout

                // Update size
                val widthDp = lerp(MIN_OVERLAY_WIDTH_DP, MAX_OVERLAY_WIDTH_DP, s.windowSizeFraction)
                val heightDp = lerp(MIN_OVERLAY_HEIGHT_DP, MAX_OVERLAY_HEIGHT_DP, s.windowSizeFraction)
                layoutParams?.width = dpToPx(widthDp)
                layoutParams?.height = dpToPx(heightDp)

                // Update background tint + transparency
                val bg = container.background as? GradientDrawable
                bg?.setColor(tintColorFromHue(s.windowTintHue, s.windowTransparency))

                // Update font
                feedLabel?.let { label ->
                    label.setTextColor(colorToInt(s.fontColor))
                    label.typeface = getTypefaceForStyle(s.fontStyle)
                }

                // Update live feed text
                feedLabel?.text = if (s.liveFeedEnabled) "Live Feed: Online" else "Live Feed: Offline"

                // Apply layout changes
                try { windowManager.updateViewLayout(container, layoutParams) } catch (_: Exception) {}
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun tintColorFromHue(hue: Float, alpha: Float): Int {
        val hsv = floatArrayOf(hue, 0.4f, 0.3f)
        val rgb = Color.HSVToColor(hsv)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    private fun colorToInt(c: androidx.compose.ui.graphics.Color): Int {
        return Color.argb(
            (c.alpha * 255).toInt(),
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt()
        )
    }

    private fun lerp(min: Int, max: Int, fraction: Float): Int =
        (min + (max - min) * fraction).toInt()

    private fun getTypefaceForStyle(style: SettingsState.FontStyleOption): Typeface {
        return when (style) {
            SettingsState.FontStyleOption.Lato -> Typeface.SANS_SERIF
            SettingsState.FontStyleOption.Roboto -> Typeface.DEFAULT
            SettingsState.FontStyleOption.Italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
    }

    // ── Drag Touch Listener ──────────────────────────────────────

    private class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val wm: WindowManager
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    return true
                }
            }
            return false
        }
    }
}
