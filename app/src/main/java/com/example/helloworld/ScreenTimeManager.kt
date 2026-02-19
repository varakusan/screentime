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
    }

    init {
        // Only initialize basic fields, don't update SettingsState here
    }

    fun loadInitialState() {
        // Load initial state
        val savedSeconds = prefs.getLong(KEY_ACCUMULATED_SECONDS, 0)
        val savedTargetHours = prefs.getInt(KEY_TARGET_HOURS, 0)
        val savedTargetMinutes = prefs.getInt(KEY_TARGET_MINUTES, 30)
        val savedDistanceTarget = prefs.getInt(KEY_DISTANCE_TARGET, 25)
        
        SettingsState.update { 
            it.copy(
                accumulatedSeconds = savedSeconds,
                targetTimeHours = savedTargetHours,
                targetTimeMinutes = savedTargetMinutes,
                distanceTargetCm = savedDistanceTarget
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

    fun startTracking() {
        if (trackingJob != null) return
        trackingJob = scope.launch {
            while (isActive) {
                delay(1000)
                SettingsState.update { it.copy(accumulatedSeconds = it.accumulatedSeconds + 1) }
                
                // Save every 60 seconds (approx)
                if (SettingsState.state.value.accumulatedSeconds % 60 == 0L) {
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
        saveToDisk()
    }

    private fun saveToDisk() {
        val currentSeconds = SettingsState.state.value.accumulatedSeconds
        prefs.edit().putLong(KEY_ACCUMULATED_SECONDS, currentSeconds).apply()
    }
}
