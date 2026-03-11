package com.example.helloworld

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
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
import android.provider.Settings
import android.util.Log
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
        private const val TAG = "OverlayService"
    }


    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var dimOverlayView: View? = null
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
                    val settings = SettingsState.state.value
                    if (settings.showScreenTime) screenTimeManager.startTracking()
                    if (settings.showLiveDistance) faceDistanceTracker.start()
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
        windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
        screenTimeManager = ScreenTimeManager(applicationContext)
        screenTimeManager.loadInitialState(force = true)
        faceDistanceTracker = FaceDistanceTracker(applicationContext, this, screenTimeManager)
        SettingsState.update { it.copy(overlayEnabled = true) }
        
        createNotificationChannel()
        updateForegroundServiceType(SettingsState.state.value.showLiveDistance)
        try {
            createOverlayView()
            createDimOverlayView()
        } catch (e: Exception) {
            // Permission might be revoked or window token invalid
            SettingsState.update { it.copy(overlayEnabled = false) }
            stopSelf()
            return
        }
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
            val settings = SettingsState.state.value
            if (settings.showScreenTime) screenTimeManager.startTracking()
            if (settings.showLiveDistance) faceDistanceTracker.start()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart service if task is removed to ensure it keeps running
        val restartServiceIntent = Intent(applicationContext, OverlayService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent = android.app.PendingIntent.getService(
            this, 1, restartServiceIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy() called")
        faceDistanceTracker.stop()
        screenTimeManager.stopTracking()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
        
        if (dimOverlayView != null) {
            try {
                windowManager.removeView(dimOverlayView)
            } catch (_: Exception) {}
            dimOverlayView = null
        }
        serviceScope.cancel()
    }

    // ── Notification ──────────────────────────────────────────────

    private fun updateForegroundServiceType(showCamera: Boolean) {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = 0
            
            if (Build.VERSION.SDK_INT >= 34) {
                serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (showCamera) {
                    serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (showCamera) {
                    serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    serviceType = 0 // location/camera/microphone are the main ones pre-34
                }
            }
            
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

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
        if (overlayView != null) return

        // Original logic for creating overlayView
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

    private fun createDimOverlayView() {
        if (dimOverlayView != null) return

        dimOverlayView = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = View.GONE
        }

        val dimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(dimOverlayView, dimParams)
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
            var lastCameraState = SettingsState.state.value.showLiveDistance
            
            SettingsState.state.collect { s ->
                if (s.showLiveDistance != lastCameraState) {
                    lastCameraState = s.showLiveDistance
                    updateForegroundServiceType(lastCameraState)
                }

                if (isScreenOn) {
                    if (s.showScreenTime) screenTimeManager.startTracking() else screenTimeManager.stopTracking()
                    if (s.showLiveDistance) faceDistanceTracker.start() else faceDistanceTracker.stop()
                }

                updateDimOverlay(s)

                overlayView ?: return@collect
                
                // Visibility
                overlayView?.visibility = if (s.overlayEnabled) View.VISIBLE else View.GONE
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
                        val hours = s.accumulatedSeconds / 3600
                        val minutes = (s.accumulatedSeconds % 3600) / 60

                        // Distance part
                        val distanceText = if (s.liveDistanceCm >= 0) {
                            String.format("%.0f", s.liveDistanceCm)
                        } else {
                            "--"  // unavailable
                        }
                        
                        val distancePart = if (s.showLiveDistance) "D: ${distanceText}cm" else ""
                        val timePart = if (s.showScreenTime) "T: ${String.format("%02d:%02d", hours, minutes)}" else ""
                        
                        val fullText = if (s.showLiveDistance && s.showScreenTime) {
                            "$distancePart | $timePart"
                        } else if (s.showLiveDistance) {
                            distancePart
                        } else if (s.showScreenTime) {
                            timePart
                        } else {
                            ""
                        }

                        if (fullText.isEmpty()) {
                            label.text = ""
                            label.visibility = View.GONE
                        } else {
                            label.visibility = View.VISIBLE
                            
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
                                Color.WHITE // Independent of theme: White when safe
                            }

                            val spannable = android.text.SpannableString(fullText)
                            
                            var currentIndex = 0
                            if (s.showLiveDistance) {
                                spannable.setSpan(
                                    android.text.style.ForegroundColorSpan(distanceColor),
                                    currentIndex, currentIndex + distancePart.length,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                currentIndex += distancePart.length
                                if (s.showScreenTime) {
                                    currentIndex += 3 // length of " | "
                                }
                            }
                            
                            if (s.showScreenTime) {
                                spannable.setSpan(
                                    android.text.style.ForegroundColorSpan(timeColor),
                                    currentIndex, currentIndex + timePart.length,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            
                            label.text = spannable
                        }
                    }
                
                statusDot?.visibility = if (s.showLiveDistance || s.showScreenTime) View.VISIBLE else View.GONE

                // Apply layout changes
                try { windowManager.updateViewLayout(container, layoutParams) } catch (_: Exception) {}
            }
        }
    }

    private fun updateDimOverlay(s: SettingsState.Settings) {
        val dimView = dimOverlayView ?: return

        if (!s.dimScreenBasedOnTime || !isScreenOn) {
            dimView.visibility = View.GONE
            return
        }

        val targetSeconds = (s.targetTimeHours * 3600) + (s.targetTimeMinutes * 60)
        if (targetSeconds <= 0) {
            dimView.visibility = View.GONE
            return
        }

        // Progress: 0f to 1.0f+
        val progress = s.accumulatedSeconds.toFloat() / targetSeconds.toFloat()
        
        // Start dimming halfway to the target time (50%)
        val startDimProgress = 0.5f 
        
        if (progress <= startDimProgress) {
            dimView.alpha = 0f
            dimView.visibility = View.GONE
            return
        }

        // Calculate mapped progress (0 to 1) between 50% and 100% of the target time
        val mappedProgress = ((progress - startDimProgress) / (1f - startDimProgress)).coerceIn(0f, 1f)

        // Find current physical hardware brightness 
        var currentBrightnessSetting = 255
        try {
            currentBrightnessSetting = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read brightness", e)
        }
        
        // As a percentage between 0 and 1
        val currentBrightnessPct = (currentBrightnessSetting / 255f).coerceIn(0.01f, 1f)
        val minBrightnessPct = s.minBrightnessPercentage / 100f

        // Let expectedPerceivedBrightness = currentPhysicalBrightness * (1 - overlayAlpha).
        // Max allowed alpha to avoid going below minBrightness:
        // maxAlpha = 1.0 - (minBrightnessPct / currentBrightnessPct)
        var maxAlpha = 1.0f - (minBrightnessPct / currentBrightnessPct)
        
        // If they manually set screen brightness *below* the limit, we shouldn't draw the overlay at all.
        maxAlpha = maxAlpha.coerceIn(0f, 1f)

        val currentAlpha = maxAlpha * mappedProgress

        dimView.alpha = currentAlpha
        dimView.visibility = View.VISIBLE
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
