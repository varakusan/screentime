package com.example.helloworld

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
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
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
class OverlayService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }


    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    // Screen Time
    private lateinit var screenTimeManager: ScreenTimeManager
    // Distance Tracker
    private lateinit var faceDistanceTracker: FaceDistanceTracker
    private var isScreenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    screenTimeManager.startTracking()
                    faceDistanceTracker.start()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenTimeManager.stopTracking()
                    faceDistanceTracker.stop()
                }
            }
        }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statusDot: View? = null
    private var feedLabel: TextView? = null
    private var dotAnimator: ObjectAnimator? = null

    // Docking state
    private var savedX = 0
    private var savedY = 0
    private var wasDocked = false

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY ensures the service restarts if killed by the system,
        // keeping the overlay alive even after the app is closed.
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenTimeManager = ScreenTimeManager(this)
        screenTimeManager.loadInitialState(force = true)
        faceDistanceTracker = FaceDistanceTracker(this, screenTimeManager)
        SettingsState.update { it.copy(overlayEnabled = true) }
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= 34) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, buildNotification(), serviceType)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        createOverlayView()
        observeSettings()
        
        // Initial screen state
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.displays
            if (displays.isNotEmpty()) {
                isScreenOn = displays[0].state == android.view.Display.STATE_ON
            }
        } catch (_: Exception) {
            isScreenOn = true
        }
        if (isScreenOn) {
            screenTimeManager.startTracking()
            faceDistanceTracker.start()
        }

        // Register screen events
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        try {
            MidnightResetReceiver.scheduleMidnightReset(this)
        } catch (_: Exception) {
            // AlarmManager exact alarm may not be available on API 31+ without permission
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        SettingsState.update { it.copy(overlayEnabled = false) }
        screenTimeManager.stopTracking()
        faceDistanceTracker.destroy()
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
                NotificationManager.IMPORTANCE_DEFAULT
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
            .setContentText("Tracking is active in the background.")
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
        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            // This can happen if permission is revoked or context is invalid during restart
            SettingsState.update { it.copy(overlayEnabled = false) }
            stopSelf()
            return
        }

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
                        layoutParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
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

                // Update font and text with progressive color
                feedLabel?.let { label ->
                    label.visibility = if (s.liveFeedEnabled) View.VISIBLE else View.GONE
                    
                    val hours = s.accumulatedSeconds / 3600
                    val minutes = (s.accumulatedSeconds % 3600) / 60

                    // Distance part: always show live distance
                    val distanceText = if (s.liveDistanceCm >= 0) {
                        String.format("%.0f", s.liveDistanceCm)
                    } else {
                        "--"  // unavailable
                    }
                    val distancePart = "D: ${distanceText}cm | "
                    val timePart = "T: ${String.format("%02d:%02d", hours, minutes)}"
                    val fullText = distancePart + timePart

                    // Calculate progress toward target
                    val targetTotalSeconds = (s.targetTimeHours * 3600L) + (s.targetTimeMinutes * 60L)
                    val progress = if (targetTotalSeconds > 0) {
                        (s.accumulatedSeconds.toFloat() / targetTotalSeconds).coerceIn(0f, 1f)
                    } else 0f

                    // Color for T: transitions from font color → yellow → orange → bright red
                    val timeColor = when {
                        progress < 0.5f -> colorToInt(s.fontColor)
                        progress < 0.7f -> Color.rgb(255, 235, 59)   // yellow
                        progress < 0.85f -> Color.rgb(255, 152, 0)   // orange
                        else -> Color.rgb(255, 23, 68)               // bright red
                    }

                    // Color for D: turns red when within target distance (too close!)
                    val distanceColor = if (s.liveDistanceCm >= 0 && s.liveDistanceCm <= s.distanceTargetCm) {
                        Color.rgb(255, 23, 68) // bright red — too close to screen!
                    } else {
                        colorToInt(s.fontColor) // normal color
                    }

                    val spannable = android.text.SpannableString(fullText)
                    // Distance part: normal or red color
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(distanceColor),
                        0, distancePart.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Time part: progressive color
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(timeColor),
                        distancePart.length, fullText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    label.text = spannable
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
