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

    fun setWindowSize(fraction: Float) {
        SettingsState.update { it.copy(windowSizeFraction = fraction.coerceIn(0.1f, 1f)) }
    }

    fun setWindowTransparency(alpha: Float) {
        SettingsState.update { it.copy(windowTransparency = alpha.coerceIn(0f, 1f)) }
    }

    fun setWindowTintHue(hue: Float) {
        SettingsState.update { it.copy(windowTintHue = hue.coerceIn(0f, 360f)) }
    }

    fun setFontStyle(style: SettingsState.FontStyleOption) {
        SettingsState.update { it.copy(fontStyle = style) }
    }

    fun setFontColor(color: Color) {
        SettingsState.update { it.copy(fontColor = color) }
    }
}
