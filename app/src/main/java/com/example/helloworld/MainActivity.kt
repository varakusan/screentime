package com.example.helloworld

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
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

    // Batch permission launcher — handles notifications + microphone in one flow
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Log results for debugging
        grants.forEach { (perm, granted) ->
            android.util.Log.d("MainActivity", "Permission $perm granted=$granted")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        SettingsState.update { it.copy(isDocked = true) }
    }

    override fun onResume() {
        super.onResume()
        SettingsState.update { it.copy(isDocked = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all runtime permissions in a single batch
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Camera permission for face tracking
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Initialize state based on saved settings
        ScreenTimeManager(this).loadInitialState()

        // Initialize state based on running service
        if (isServiceRunning(OverlayService::class.java)) {
            SettingsState.update { it.copy(overlayEnabled = true) }
        }

        setContent {
            HelloWorldTheme {
                MainScreen(
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

    internal fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    internal fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        // If the above doesn't work (some OEMs), just open general settings
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun stopOverlayService() {
        SettingsState.update { it.copy(overlayEnabled = false) }
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

// ═══════════════════════════════════════════════════════════════
//  MAIN SCREEN — Bottom Navigation container
// ═══════════════════════════════════════════════════════════════

@Composable
fun MainScreen(onOverlayToggle: (Boolean) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D1420),
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Filled.Settings, "Settings",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.helloworld.ui.theme.AccentCyan,
                        selectedTextColor = com.example.helloworld.ui.theme.AccentCyan,
                        unselectedIconColor = Color.White.copy(alpha = 0.45f),
                        unselectedTextColor = Color.White.copy(alpha = 0.45f),
                        indicatorColor = com.example.helloworld.ui.theme.AccentCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Filled.BarChart, "History",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("History", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.helloworld.ui.theme.AccentCyan,
                        selectedTextColor = com.example.helloworld.ui.theme.AccentCyan,
                        unselectedIconColor = Color.White.copy(alpha = 0.45f),
                        unselectedTextColor = Color.White.copy(alpha = 0.45f),
                        indicatorColor = com.example.helloworld.ui.theme.AccentCyan.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> SettingsScreen(onOverlayToggle = onOverlayToggle)
                1 -> HistoryScreen()
            }
        }
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
    val focusManager = LocalFocusManager.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }
    val activity = LocalContext.current as MainActivity

    // Local states for target inputs to ensure smooth typing and sync
    var hoursInput by remember { mutableStateOf(TextFieldValue(settings.targetTimeHours.toString())) }
    var minutesInput by remember { mutableStateOf(TextFieldValue(settings.targetTimeMinutes.toString())) }
    var isHoursFocused by remember { mutableStateOf(false) }
    var isMinutesFocused by remember { mutableStateOf(false) }

    // Sync from settings (e.g. slider) back to text fields only when NOT focused
    LaunchedEffect(settings.targetTimeHours, isHoursFocused) {
        if (!isHoursFocused && settings.targetTimeHours.toString() != hoursInput.text) {
            hoursInput = TextFieldValue(settings.targetTimeHours.toString())
        }
    }
    LaunchedEffect(settings.targetTimeMinutes, isMinutesFocused) {
        if (!isMinutesFocused && settings.targetTimeMinutes.toString() != minutesInput.text) {
            minutesInput = TextFieldValue(settings.targetTimeMinutes.toString())
        }
    }

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
                .fillMaxWidth(0.96f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Settings Card
            GlassCard {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. OVERLAY TOGGLE
                    SettingsToggleRow(
                        icon = Icons.Filled.Layers,
                        label = "Overlay Window",
                        checked = settings.overlayEnabled,
                        accentColor = AccentCyan,
                        onCheckedChange = { onOverlayToggle(it) }
                    )

                    GlassDivider()

                    // 2. LIVE FEED TOGGLE
                    SettingsToggleRow(
                        icon = Icons.Filled.LiveTv,
                        label = "Live Feed",
                        checked = settings.liveFeedEnabled,
                        accentColor = AccentPink,
                        onCheckedChange = { vm.setLiveFeedEnabled(it) }
                    )

                    GlassDivider()

                    // 3. TRANSPARENCY SLIDER
                    SettingsSliderRow(
                        icon = Icons.Filled.Opacity,
                        label = "Transparency",
                        value = 1f - settings.windowTransparency,
                        accentColor = AccentCyan,
                        onValueChange = { vm.setWindowTransparency(1f - it, activity) }
                    )

                    GlassDivider()

                    // 4. WINDOW TINT SLIDER
                    SettingsSliderRow(
                        icon = Icons.Filled.Palette,
                        label = "Window Tint",
                        value = settings.windowTintHue / 360f,
                        accentColor = AccentYellow,
                        onValueChange = { vm.setWindowTintHue(it * 360f, activity) }
                    )

                    GlassDivider()

                    // 5. DISTANCE TARGET (0-100 cm)
                    SettingsSliderRow(
                        icon = Icons.Filled.Straighten,
                        label = "Distance Target (cm)",
                        value = settings.distanceTargetCm / 100f,
                        accentColor = AccentPurple,
                        onValueChange = { vm.setDistanceTarget((it * 100).toInt(), activity) }
                    )

                    GlassDivider()

                    // 6 & 7: ACTIVE SCREEN TIME STATUS
                    val hasUsagePermission = activity.checkUsageStatsPermission()

                    // Calculate progress toward target
                    val targetTotalSeconds = (settings.targetTimeHours * 3600L) + (settings.targetTimeMinutes * 60L)
                    val progress = if (targetTotalSeconds > 0) {
                        (settings.accumulatedSeconds.toFloat() / targetTotalSeconds).coerceIn(0f, 1f)
                    } else 0f
                    // Color transitions: green → yellow → red as progress goes 0 → 0.7 → 1.0
                    val screenTimeColor = when {
                        progress < 0.5f -> AccentGreen
                        progress < 0.7f -> Color(0xFFFFEB3B) // yellow
                        progress < 0.85f -> Color(0xFFFF9800) // orange
                        else -> Color(0xFFFF1744) // bright red
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AccessTime,
                            null,
                            tint = if (hasUsagePermission) screenTimeColor else AccentPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Active Screen Time",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (hasUsagePermission) "Tracking active" else "Usage access required",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                        if (!hasUsagePermission) {
                            Text(
                                "Grant",
                                color = AccentPink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { activity.requestUsageStatsPermission() }
                                    .padding(8.dp)
                            )
                        } else {
                            val hours = settings.accumulatedSeconds / 3600
                            val minutes = (settings.accumulatedSeconds % 3600) / 60
                            Text(
                                String.format("%02dh %02dm", hours, minutes),
                                color = screenTimeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // TARGET TIME SLIDERS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            null,
                            tint = AccentYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Target: ${String.format("%02d", settings.targetTimeHours)}h ${String.format("%02d", settings.targetTimeMinutes)}m",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(100.dp)
                        )
                        // Hours slider
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hours", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                                // Manual Input
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = hoursInput,
                                        onValueChange = { newVal ->
                                            val s = newVal.text
                                            if (s.isEmpty()) {
                                                hoursInput = newVal
                                                vm.setTargetTimeHours(0, activity)
                                            } else {
                                                val num = s.toIntOrNull()
                                                if (num != null) {
                                                    val capped = num.coerceIn(0, 23)
                                                    // If we capped it, we update the local text to show the cap
                                                    if (capped != num) {
                                                        hoursInput = TextFieldValue(capped.toString(), selection = newVal.selection)
                                                    } else {
                                                        hoursInput = newVal
                                                    }
                                                    vm.setTargetTimeHours(capped, activity)
                                                }
                                            }
                                        },
                                        modifier = Modifier.onFocusChanged { 
                                            isHoursFocused = it.isFocused 
                                            if (it.isFocused) {
                                                hoursInput = TextFieldValue("")
                                            }
                                        },
                                        textStyle = TextStyle(
                                            color = AccentYellow,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { focusManager.clearFocus() }
                                        ),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentYellow)
                                    )
                                }
                            }
                            Slider(
                                value = settings.targetTimeHours / 23f,
                                onValueChange = { vm.setTargetTimeHours((it * 23).toInt(), activity) },
                                modifier = Modifier.height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentYellow,
                                    activeTrackColor = AccentYellow
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Minutes slider
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Min", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                                // Manual Input
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = minutesInput,
                                        onValueChange = { newVal ->
                                            val s = newVal.text
                                            if (s.isEmpty()) {
                                                minutesInput = newVal
                                                vm.setTargetTimeMinutes(0, activity)
                                            } else {
                                                val num = s.toIntOrNull()
                                                if (num != null) {
                                                    val capped = num.coerceIn(0, 59)
                                                    if (capped != num) {
                                                        minutesInput = TextFieldValue(capped.toString(), selection = newVal.selection)
                                                    } else {
                                                        minutesInput = newVal
                                                    }
                                                    vm.setTargetTimeMinutes(capped, activity)
                                                }
                                            }
                                        },
                                        modifier = Modifier.onFocusChanged { 
                                            isMinutesFocused = it.isFocused 
                                            if (it.isFocused) {
                                                minutesInput = TextFieldValue("")
                                            }
                                        },
                                        textStyle = TextStyle(
                                            color = AccentYellow,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { focusManager.clearFocus() }
                                        ),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentYellow)
                                    )
                                }
                            }
                            Slider(
                                value = settings.targetTimeMinutes / 59f,
                                onValueChange = { vm.setTargetTimeMinutes((it * 59).toInt(), activity) },
                                modifier = Modifier.height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentYellow,
                                    activeTrackColor = AccentYellow
                                )
                            )
                        }
                    }

                    GlassDivider()

                    // 8. SHAPE & COLOR CLICKABLE TITLES
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Clickable Shape Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showShapePicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PhotoSizeSelectLarge, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shape", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Clickable Color Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showColorPicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            ColorWheelWithPlus(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Color", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Screen Overlay v1.0",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 10.sp
            )
        }

        if (showColorPicker) {
            val initialColor = remember { settings.fontColor }
            EditColorsDialog(
                initialColor = initialColor,
                onColorChange = { vm.setFontColor(it, activity) },
                onColorSelected = { 
                    vm.setFontColor(it, activity)
                    val newCustom = (listOf(it) + settings.customColors).distinct().take(14)
                    vm.updateCustomColors(newCustom)
                    showColorPicker = false 
                },
                onDismiss = { 
                    vm.setFontColor(initialColor, activity)
                    showColorPicker = false 
                }
            )
        }

        if (showShapePicker) {
            ShapeSelectionDialog(
                initialShape = settings.windowShape,
                onShapeSelected = { 
                    vm.setWindowShape(it, activity)
                    showShapePicker = false 
                },
                onDismiss = { showShapePicker = false }
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

// ── MS Paint Style Color Picker ─────────────────────────────────

@Composable
fun EditColorsDialog(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember { 
        val hsvRes = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvRes)
        mutableStateOf(hsvRes) 
    }
    
    val currentColor = Color.hsv(hsv[0], hsv[1], hsv[2])
    
    // Trigger real-time update
    LaunchedEffect(currentColor) {
        onColorChange(currentColor)
    }

    var hexText by remember { mutableStateOf(String.format("#%06X", (0xFFFFFF and initialColor.toArgb()))) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Edit Colors", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Spectrum
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                )
                            )
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                    val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                    hsv = floatArrayOf((x / size.width) * 360f, 1f - (y / size.height), hsv[2])
                                    hexText = String.format("#%06X", (0xFFFFFF and currentColor.toArgb()))
                                }
                            }
                    ) {
                        // Gray overlay for saturation
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.White)
                                    )
                                )
                        )
                    }
                    
                    // Luminosity Slider
                    Column(
                        modifier = Modifier.width(30.dp).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.White, currentColor.copy(alpha = 1f), Color.Black)
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                        hsv = floatArrayOf(hsv[0], hsv[1], 1f - (y / size.height))
                                        hexText = String.format("#%06X", (0xFFFFFF and currentColor.toArgb()))
                                    }
                                }
                        )
                    }
                }
                
                // RGB & Hex Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rgb = arrayOf(
                        (currentColor.red * 255).toInt(),
                        (currentColor.green * 255).toInt(),
                        (currentColor.blue * 255).toInt()
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hex", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        TextField(
                            value = hexText,
                            onValueChange = { hexText = it },
                            modifier = Modifier.height(36.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedIndicatorColor = AccentCyan,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                    
                    listOf("R", "G", "B").forEachIndexed { i, label ->
                        Column(modifier = Modifier.width(40.dp)) {
                            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${rgb[i]}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
                
                // OK / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentCyan)
                            .clickable { onColorSelected(currentColor) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ShapeSelectionDialog(
    initialShape: SettingsState.WindowShape,
    onShapeSelected: (SettingsState.WindowShape) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Shape", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SettingsState.WindowShape.values().forEach { shape ->
                        val selected = initialShape == shape
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onShapeSelected(shape) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AccentPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) AccentPurple else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val color = if (selected) AccentPurple else Color.White.copy(alpha = 0.7f)
                                    when (shape) {
                                        SettingsState.WindowShape.Rectangle -> drawRect(color)
                                        SettingsState.WindowShape.Rounded -> drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shape.name,
                                color = if (selected) AccentPurple else Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ColorWheelWithPlus(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sweepGradient = Brush.sweepGradient(
                colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            )
            drawCircle(brush = sweepGradient)
        }
        Box(
            modifier = Modifier
                .offset(4.dp, (-4).dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.Gray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(10.dp)
            )
        }
    }
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
            .padding(vertical = 2.dp),
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
                fontSize = 15.sp
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
    Column(modifier = Modifier.padding(vertical = 1.dp)) {
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Text(
                text = label.let { 
                    if (it.contains("cm")) "${(value * 100).toInt()}cm"
                    else if (it.contains("Hours")) "${(value * 23).toInt()}h"
                    else if (it.contains("Minutes")) "${(value * 59).toInt()}m"
                    else "${(value * 100).toInt()}%"
                },
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.15f)
            )
        )
    }
}
