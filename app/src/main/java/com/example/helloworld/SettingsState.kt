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
    enum class WindowShape { Rectangle, Rounded }
    
    data class Settings(
        val overlayEnabled: Boolean = false,
        val liveFeedEnabled: Boolean = true,
        val windowTransparency: Float = 0.7f,    // 0f..1f  (alpha)
        val windowTintHue: Float = 200f,         // 0..360 hue
        val fontColor: Color = Color.White,
        val windowShape: WindowShape = WindowShape.Rounded,
        val isDocked: Boolean = false,
        val distanceTargetCm: Int = 25,          // User-set distance target
        val liveDistanceCm: Float = -1f,         // Live measured distance (-1 = unavailable)
        val trackerActive: Boolean = false,      // Whether the distance tracker is running
        val timeHours: Int = 0,
        val timeMinutes: Int = 30,
        val accumulatedSeconds: Long = 0,
        val targetTimeHours: Int = 0,
        val targetTimeMinutes: Int = 30,
        val customColors: List<Color> = listOf(
            Color(0xFF000000), Color(0xFF808080), Color(0xFF800000), Color(0xFF808000),
            Color(0xFF008000), Color(0xFF008080), Color(0xFF000080), Color(0xFF800080),
            Color(0xFF808040), Color(0xFF004040), Color(0xFF0080FF), Color(0xFF004080),
            Color(0xFF4000FF), Color(0xFF804000)
        )
    )

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        _state.value = transform(_state.value)
    }
}
