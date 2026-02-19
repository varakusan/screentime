package com.example.helloworld

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel that exposes SettingsState to Compose and provides
 * update helpers for each setting.
 */
class SettingsViewModel : ViewModel() {

    val settings: StateFlow<SettingsState.Settings> = SettingsState.state

    fun setOverlayEnabled(enabled: Boolean) {
        SettingsState.update { it.copy(overlayEnabled = enabled) }
    }

    fun setLiveFeedEnabled(enabled: Boolean) {
        SettingsState.update { it.copy(liveFeedEnabled = enabled) }
    }


    fun setWindowTransparency(alpha: Float, context: android.content.Context) {
        val a = alpha.coerceIn(0f, 1f)
        SettingsState.update { it.copy(windowTransparency = a) }
        ScreenTimeManager(context).saveWindowTransparency(a)
    }

    fun setWindowTintHue(hue: Float, context: android.content.Context) {
        val h = hue.coerceIn(0f, 360f)
        SettingsState.update { it.copy(windowTintHue = h) }
        ScreenTimeManager(context).saveWindowTintHue(h)
    }

    fun setFontColor(color: Color, context: android.content.Context) {
        SettingsState.update { it.copy(fontColor = color) }
        ScreenTimeManager(context).saveFontColor(color)
    }

    fun updateCustomColors(colors: List<Color>) {
        SettingsState.update { it.copy(customColors = colors) }
    }

    fun setAccumulatedTime(seconds: Long) {
        SettingsState.update { it.copy(accumulatedSeconds = seconds) }
    }

    fun setWindowShape(shape: SettingsState.WindowShape, context: android.content.Context) {
        SettingsState.update { it.copy(windowShape = shape) }
        ScreenTimeManager(context).saveWindowShape(shape)
    }

    fun setDocked(docked: Boolean) {
        SettingsState.update { it.copy(isDocked = docked) }
    }

    fun setDistanceTarget(cm: Int, context: android.content.Context) {
        val capped = cm.coerceIn(0, 100)
        SettingsState.update { it.copy(distanceTargetCm = capped) }
        ScreenTimeManager(context).saveDistanceTarget(capped)
    }

    fun setTimeHours(hours: Int) {
        SettingsState.update { it.copy(timeHours = hours.coerceIn(0, 23)) }
    }

    fun setTimeMinutes(minutes: Int) {
        SettingsState.update { it.copy(timeMinutes = minutes.coerceIn(0, 59)) }
    }

    fun setTargetTimeHours(hours: Int, context: android.content.Context) {
        val h = hours.coerceIn(0, 23)
        SettingsState.update { it.copy(targetTimeHours = h) }
        ScreenTimeManager(context).saveTargetTime(h, SettingsState.state.value.targetTimeMinutes)
    }

    fun setTargetTimeMinutes(minutes: Int, context: android.content.Context) {
        val m = minutes.coerceIn(0, 59)
        SettingsState.update { it.copy(targetTimeMinutes = m) }
        ScreenTimeManager(context).saveTargetTime(SettingsState.state.value.targetTimeHours, m)
    }
}
