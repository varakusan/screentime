package com.example.helloworld

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared observable settings state — singleton accessible from both
 * the Compose UI (via ViewModel) and the OverlayService.
 */
object SettingsState {

    enum class FontStyleOption { Lato, Roboto, Italic }

    data class Settings(
        val overlayEnabled: Boolean = false,
        val liveFeedEnabled: Boolean = true,
        val windowSizeFraction: Float = 0.5f,   // 0f..1f
        val windowTransparency: Float = 0.7f,    // 0f..1f  (alpha)
        val windowTintHue: Float = 200f,         // 0..360 hue
        val fontStyle: FontStyleOption = FontStyleOption.Roboto,
        val fontColor: Color = Color.White
    )

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        _state.value = transform(_state.value)
    }
}
