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
    enum class WindowShape { Rectangle, Rounded, Pill, Circle }
    
    enum class OverlayColor(val color: Color) {
        White(Color.White),
        Yellow(Color(0xFFFFEB3B)),
        Cyan(Color(0xFF00E5FF)),
        Green(Color(0xFF00E676)),
        Pink(Color(0xFFFF4081)),
        Purple(Color(0xFFD500F9)),
        Orange(Color(0xFFFF9100)),
        Red(Color(0xFFFF1744)),
        Blue(Color(0xFF2979FF)),
        Teal(Color(0xFF1DE9B6)),
        Indigo(Color(0xFF3D5AFE))
    }

    data class Settings(
        val overlayEnabled: Boolean = false,
        val liveFeedEnabled: Boolean = true,
        val windowSizeFraction: Float = 0.5f,   // 0f..1f
        val windowTransparency: Float = 0.7f,    // 0f..1f  (alpha)
        val windowTintHue: Float = 200f,         // 0..360 hue
        val fontColor: OverlayColor = OverlayColor.White,
        val windowShape: WindowShape = WindowShape.Rounded,
        val isDocked: Boolean = false
    )

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        _state.value = transform(_state.value)
    }
}
