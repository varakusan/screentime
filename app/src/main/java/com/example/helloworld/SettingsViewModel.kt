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


    fun setWindowTransparency(alpha: Float) {
        SettingsState.update { it.copy(windowTransparency = alpha.coerceIn(0f, 1f)) }
    }

    fun setWindowTintHue(hue: Float) {
        SettingsState.update { it.copy(windowTintHue = hue.coerceIn(0f, 360f)) }
    }

    fun setFontColor(color: Color) {
        SettingsState.update { it.copy(fontColor = color) }
    }

    fun updateCustomColors(colors: List<Color>) {
        SettingsState.update { it.copy(customColors = colors) }
    }

    fun setWindowShape(shape: SettingsState.WindowShape) {
        SettingsState.update { it.copy(windowShape = shape) }
    }

    fun setDocked(docked: Boolean) {
        SettingsState.update { it.copy(isDocked = docked) }
    }

    fun setDistance(cm: Int) {
        SettingsState.update { it.copy(distanceCm = cm.coerceIn(0, 100)) }
    }

    fun setTimeHours(hours: Int) {
        SettingsState.update { it.copy(timeHours = hours.coerceIn(0, 23)) }
    }

    fun setTimeMinutes(minutes: Int) {
        SettingsState.update { it.copy(timeMinutes = minutes.coerceIn(0, 59)) }
    }
}
