package com.example.helloworld

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenTimeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screen_time_prefs", Context.MODE_PRIVATE)
    private var trackingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val KEY_ACCUMULATED_SECONDS = "accumulated_seconds"
        private const val KEY_LAST_SAVE_TIME = "last_save_time"
        private const val KEY_TARGET_HOURS = "target_hours"
        private const val KEY_TARGET_MINUTES = "target_minutes"
        private const val KEY_DISTANCE_TARGET = "distance_target_cm"
        private const val KEY_WINDOW_TRANSPARENCY = "window_transparency"
        private const val KEY_WINDOW_TINT_HUE = "window_tint_hue"
        private const val KEY_FONT_COLOR = "font_color"
        private const val KEY_WINDOW_SHAPE = "window_shape"
        private const val KEY_DISTANCE_VIOLATIONS = "distance_violations_today"
    }

    init {
        // Only initialize basic fields, don't update SettingsState here
    }

    fun loadInitialState(force: Boolean = false) {
        // Guard: If we are already tracking in this process, dont overwrite from disk
        // unless explicitly forced (e.g. from OverlayService.onCreate)
        if (!force && SettingsState.state.value.trackerActive) return

        // Load initial state
        val savedSeconds = prefs.getLong(KEY_ACCUMULATED_SECONDS, 0)
        // ... (rest of the load logic stays same but with the guard)
        val savedTargetHours = prefs.getInt(KEY_TARGET_HOURS, 0)
        val savedTargetMinutes = prefs.getInt(KEY_TARGET_MINUTES, 30)
        val savedDistanceTarget = prefs.getInt(KEY_DISTANCE_TARGET, 25)
        val savedTransparency = prefs.getFloat(KEY_WINDOW_TRANSPARENCY, 0.7f)
        val savedHue = prefs.getFloat(KEY_WINDOW_TINT_HUE, 200f)
        val savedFontColorLong = prefs.getLong(KEY_FONT_COLOR, 0xFFFFFFFF) // White
        val savedShape = prefs.getString(KEY_WINDOW_SHAPE, SettingsState.WindowShape.Rounded.name)
        
        SettingsState.update { 
            it.copy(
                accumulatedSeconds = savedSeconds,
                targetTimeHours = savedTargetHours,
                targetTimeMinutes = savedTargetMinutes,
                distanceTargetCm = savedDistanceTarget,
                windowTransparency = savedTransparency,
                windowTintHue = savedHue,
                fontColor = androidx.compose.ui.graphics.Color(savedFontColorLong.toULong()),
                windowShape = if (savedShape == SettingsState.WindowShape.Rectangle.name) 
                    SettingsState.WindowShape.Rectangle else SettingsState.WindowShape.Rounded
            ) 
        }
    }

    fun saveTargetTime(hours: Int, minutes: Int) {
        prefs.edit()
            .putInt(KEY_TARGET_HOURS, hours)
            .putInt(KEY_TARGET_MINUTES, minutes)
            .apply()
    }

    fun saveDistanceTarget(cm: Int) {
        prefs.edit()
            .putInt(KEY_DISTANCE_TARGET, cm)
            .apply()
    }

    fun saveWindowTransparency(alpha: Float) {
        prefs.edit().putFloat(KEY_WINDOW_TRANSPARENCY, alpha).apply()
    }

    fun saveWindowTintHue(hue: Float) {
        prefs.edit().putFloat(KEY_WINDOW_TINT_HUE, hue).apply()
    }

    fun saveFontColor(color: androidx.compose.ui.graphics.Color) {
        prefs.edit().putLong(KEY_FONT_COLOR, color.value.toLong()).apply()
    }

    fun saveWindowShape(shape: SettingsState.WindowShape) {
        prefs.edit().putString(KEY_WINDOW_SHAPE, shape.name).apply()
    }

    fun startTracking() {
        if (trackingJob != null) return
        trackingJob = scope.launch {
            while (isActive) {
                delay(1000)
                SettingsState.update { it.copy(accumulatedSeconds = it.accumulatedSeconds + 1) }
                
                // Save more frequently (every 5 seconds) to minimize data loss on process death
                val current = SettingsState.state.value.accumulatedSeconds
                if (current % 5 == 0L) {
                    saveToDisk()
                }
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        saveToDisk()
    }

    fun reset() {
        SettingsState.update { it.copy(accumulatedSeconds = 0) }
        prefs.edit()
            .putLong(KEY_ACCUMULATED_SECONDS, 0L)
            .putInt(KEY_DISTANCE_VIOLATIONS, 0)
            .apply()
    }

    /** Increments the distance violation counter and immediately persists it. */
    fun recordDistanceViolation() {
        val current = prefs.getInt(KEY_DISTANCE_VIOLATIONS, 0)
        prefs.edit().putInt(KEY_DISTANCE_VIOLATIONS, current + 1).apply()
    }

    /** Returns how many distance violations have been recorded today so far. */
    fun getDistanceViolationCount(): Int = prefs.getInt(KEY_DISTANCE_VIOLATIONS, 0)

    private fun saveToDisk() {
        val currentSeconds = SettingsState.state.value.accumulatedSeconds
        prefs.edit().putLong(KEY_ACCUMULATED_SECONDS, currentSeconds).apply()
    }
}
