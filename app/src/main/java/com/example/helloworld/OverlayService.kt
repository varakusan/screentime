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
    }


    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statusDot: View? = null
    private var feedLabel: TextView? = null
    private var dotAnimator: ObjectAnimator? = null

    // Docking state
    private var savedX = 0
    private var savedY = 0
    private var wasDocked = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY ensures the service restarts if killed by the system,
        // keeping the overlay alive even after the app is closed.
        return START_STICKY
    }

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
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
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

        // Label — single line, no wrapping
        val label = TextView(this).apply {
            text = "D: 0cm | T: 00:00"
            setTextColor(colorToInt(settings.fontColor))
            textSize = 13f
            isSingleLine = true
            maxLines = 1
        }
        feedLabel = label

        innerLayout.addView(dot, dotParams)
        innerLayout.addView(label)
        container.addView(innerLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // Window params — both dimensions wrap content to fit text
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dpToPx(40)
        }
        layoutParams = params

        // Touch listener for drag
        container.setOnTouchListener(DragTouchListener(params, windowManager, this))

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
                val bg = container.background as? GradientDrawable

                // 1. Handle Shape
                when (s.windowShape) {
                    SettingsState.WindowShape.Rectangle -> {
                        bg?.shape = GradientDrawable.RECTANGLE
                        bg?.cornerRadius = 0f
                    }
                    SettingsState.WindowShape.Rounded -> {
                        bg?.shape = GradientDrawable.RECTANGLE
                        bg?.cornerRadius = dpToPx(16).toFloat()
                    }
                    SettingsState.WindowShape.Pill -> {
                        bg?.shape = GradientDrawable.RECTANGLE
                        bg?.cornerRadius = dpToPx(100).toFloat()
                    }
                    SettingsState.WindowShape.Circle -> {
                        bg?.shape = GradientDrawable.OVAL
                        bg?.cornerRadius = 0f
                    }
                }

                // 2. Size — both wrap content to fit text
                layoutParams?.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT

                if (s.isDocked != wasDocked) {
                    if (s.isDocked) {
                        // Saving position
                        savedX = layoutParams?.x ?: 0
                        savedY = layoutParams?.y ?: 0
                        
                        // Dock to top
                        layoutParams?.x = 0
                        layoutParams?.y = 0 
                        layoutParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    } else {
                        // Restoring position
                        layoutParams?.x = savedX
                        layoutParams?.y = savedY
                        layoutParams?.gravity = Gravity.TOP or Gravity.START // reset gravity if needed, but existing is TOP|CENTER_HORIZONTAL. 
                        // Wait, onCreate sets TOP|CENTER. If user drags, x/y are offsets from that.
                        // If I change gravity, coordinates might mean different things.
                        // The DragListener updates x/y based on raw movement.
                        // Let's keep gravity consistent.
                    }
                    wasDocked = s.isDocked
                } else if (s.isDocked) {
                    // Force position if docked (in case of other updates)
                    layoutParams?.x = 0
                    layoutParams?.y = 0
                }

                // Update background tint + transparency
                bg?.setColor(tintColorFromHue(s.windowTintHue, s.windowTransparency))

                // Update font and text
                feedLabel?.let { label ->
                    label.visibility = if (s.liveFeedEnabled) View.VISIBLE else View.GONE
                    label.setTextColor(colorToInt(s.fontColor))
                    label.text = "D: ${s.distanceCm}cm | T: ${String.format("%02d:%02d", s.timeHours, s.timeMinutes)}"
                }
                
                statusDot?.visibility = if (s.liveFeedEnabled) View.VISIBLE else View.GONE

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



    // ── Drag Touch Listener ──────────────────────────────────────

    private class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val wm: WindowManager,
        private val context: android.content.Context
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var isDragging = false
        private val CLICK_THRESHOLD = 10 // dp

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - touchX)
                    val dy = Math.abs(event.rawY - touchY)
                    val threshold = CLICK_THRESHOLD * view.resources.displayMetrics.density
                    if (dx > threshold || dy > threshold) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Tap detected — launch the app
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(intent)
                    }
                    return true
                }
            }
            return false
        }
    }
}
