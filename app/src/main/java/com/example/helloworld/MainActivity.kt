package com.example.helloworld

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helloworld.ui.theme.AccentCyan
import com.example.helloworld.ui.theme.AccentGreen
import com.example.helloworld.ui.theme.AccentPink
import com.example.helloworld.ui.theme.AccentPurple
import com.example.helloworld.ui.theme.AccentYellow
import com.example.helloworld.ui.theme.GlassBg
import com.example.helloworld.ui.theme.GlassBorder
import com.example.helloworld.ui.theme.GlassHighlight
import com.example.helloworld.ui.theme.HelloWorldTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from overlay permission settings, check if granted
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            SettingsState.update { it.copy(overlayEnabled = false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        setContent {
            HelloWorldTheme {
                SettingsScreen(
                    onOverlayToggle = { enabled ->
                        if (enabled) {
                            if (Settings.canDrawOverlays(this)) {
                                startOverlayService()
                            } else {
                                requestOverlayPermission()
                            }
                        } else {
                            stopOverlayService()
                        }
                    }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startOverlayService() {
        SettingsState.update { it.copy(overlayEnabled = true) }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        SettingsState.update { it.copy(overlayEnabled = false) }
        stopService(Intent(this, OverlayService::class.java))
    }
}

// ═══════════════════════════════════════════════════════════════
//  SETTINGS SCREEN — Glassmorphism Panel
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onOverlayToggle: (Boolean) -> Unit
) {
    val settings by vm.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x40000020),
                        Color(0x60000030),
                        Color(0x80000020)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "⚙ Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ── Glass Card ──────────────────────────────────────
            GlassCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // OVERLAY TOGGLE
                    SettingsToggleRow(
                        icon = Icons.Filled.Layers,
                        label = "Overlay Window",
                        checked = settings.overlayEnabled,
                        accentColor = AccentCyan,
                        onCheckedChange = { onOverlayToggle(it) }
                    )

                    GlassDivider()

                    // LIVE FEED TOGGLE
                    SettingsToggleRow(
                        icon = Icons.Filled.LiveTv,
                        label = "Live Feed",
                        checked = settings.liveFeedEnabled,
                        accentColor = AccentPink,
                        onCheckedChange = { vm.setLiveFeedEnabled(it) }
                    )

                    GlassDivider()

                    // WINDOW SIZE SLIDER
                    SettingsSliderRow(
                        icon = Icons.Filled.PhotoSizeSelectLarge,
                        label = "Window Size",
                        value = settings.windowSizeFraction,
                        accentColor = AccentPurple,
                        onValueChange = { vm.setWindowSize(it) }
                    )

                    GlassDivider()

                    // TRANSPARENCY SLIDER
                    SettingsSliderRow(
                        icon = Icons.Filled.Opacity,
                        label = "Transparency",
                        value = settings.windowTransparency,
                        accentColor = AccentCyan,
                        onValueChange = { vm.setWindowTransparency(it) }
                    )

                    GlassDivider()

                    // WINDOW TINT/COLOR SLIDER
                    SettingsSliderRow(
                        icon = Icons.Filled.Palette,
                        label = "Window Tint",
                        value = settings.windowTintHue / 360f,
                        accentColor = AccentYellow,
                        onValueChange = { vm.setWindowTintHue(it * 360f) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Font Settings Card ──────────────────────────────
            GlassCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TextFields,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Font Style",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }

                    // Font style chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsState.FontStyleOption.values().forEach { option ->
                            val selected = settings.fontStyle == option
                            FilterChip(
                                selected = selected,
                                onClick = { vm.setFontStyle(option) },
                                label = {
                                    Text(
                                        text = option.name,
                                        fontStyle = if (option == SettingsState.FontStyleOption.Italic)
                                            FontStyle.Italic else FontStyle.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen.copy(alpha = 0.25f),
                                    selectedLabelColor = AccentGreen,
                                    containerColor = Color.Transparent,
                                    labelColor = Color.White.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = if (selected) AccentGreen.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            )
                        }
                    }

                    GlassDivider()

                    // Font color presets
                    Text(
                        "Font Color",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            Color.White to "White",
                            AccentYellow to "Yellow",
                            AccentCyan to "Cyan",
                            AccentGreen to "Green",
                            AccentPink to "Pink"
                        )
                        presets.forEach { (color, _) ->
                            val isSelected = settings.fontColor == color
                            val animatedBorderAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0f,
                                animationSpec = tween(300),
                                label = "borderAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = 3.dp,
                                        color = Color.White.copy(alpha = animatedBorderAlpha),
                                        shape = CircleShape
                                    )
                                    .clickable { vm.setFontColor(color) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version label
            Text(
                text = "Screen Overlay v1.0",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassBg)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(GlassBorder, Color.Transparent)
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Top highlight strip for glass effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            GlassHighlight,
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val animatedIconColor by animateColorAsState(
        targetValue = if (checked) accentColor else Color.White.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "iconColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = animatedIconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SettingsSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "${(value * 100).toInt()}%",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.15f)
            )
        )
    }
}
