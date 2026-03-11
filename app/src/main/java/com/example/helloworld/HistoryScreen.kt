package com.example.helloworld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private enum class HistoryPeriod(val label: String) {
    Day("Day"), Week("Week"), Month("Month"), Year("Year"), Custom("Custom")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val analytics = remember { AnalyticsManager(context) }
    val screenTimeManager = remember { ScreenTimeManager(context) }

    var selectedPeriod by remember { mutableStateOf(HistoryPeriod.Week) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    
    // Custom start/end dates for Custom Range
    var customStartDate by remember { mutableStateOf<Date?>(null) }
    var customEndDate by remember { mutableStateOf<Date?>(null) }

    val liveState by SettingsState.state.collectAsState()
    val todayViolations = remember(liveState) { screenTimeManager.getDistanceViolationCount() }

    // Derive range and data
    val calendar = Calendar.getInstance()
    
    // Calculate display labels for all default ranges
    val weekRangeLabel = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val end = cal.time
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        "${sdf.format(start)} - ${sdf.format(end)}"
    }
    val monthRangeLabel = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }
    val yearRangeLabel = remember {
        SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    }

    val rangeData: List<AnalyticsManager.DayRecord> = remember(selectedPeriod, customStartDate, customEndDate) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            HistoryPeriod.Day -> listOf(analytics.getRecord(analytics.getTodayDate()))
            HistoryPeriod.Week -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = cal.time
                val records = mutableListOf<AnalyticsManager.DayRecord>()
                for (i in 0..6) {
                    records.add(analytics.getRecord(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                records
            }
            HistoryPeriod.Month -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val records = mutableListOf<AnalyticsManager.DayRecord>()
                for (i in 0 until days) {
                    records.add(analytics.getRecord(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                records
            }
            HistoryPeriod.Year -> {
                // For year, we might just show last 12 months aggregated if daily is too much, 
                // but prompt says "daily data should be displayd". 
                // We'll fetch daily for current year.
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val days = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
                val records = mutableListOf<AnalyticsManager.DayRecord>()
                for (i in 0 until days) {
                    records.add(analytics.getRecord(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                records
            }
            HistoryPeriod.Custom -> {
                if (customStartDate != null && customEndDate != null) {
                    analytics.getDailyRecordsForRange(customStartDate!!, customEndDate!!)
                } else emptyList()
            }
        }
    }

    val rangeLabel = remember(selectedPeriod, rangeData, weekRangeLabel, monthRangeLabel, yearRangeLabel) {
        when (selectedPeriod) {
            HistoryPeriod.Week -> weekRangeLabel
            HistoryPeriod.Month -> monthRangeLabel
            HistoryPeriod.Year -> yearRangeLabel
            HistoryPeriod.Day -> "Today"
            HistoryPeriod.Custom -> {
                if (rangeData.isEmpty()) "No range selected"
                else {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    try {
                        "${sdf.format(sdfInput.parse(rangeData.first().date)!!)} - ${sdf.format(sdfInput.parse(rangeData.last().date)!!)}"
                    } catch (_: Exception) { "Custom Range" }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Header ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Usage Activity",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showCustomRangePicker = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DateRange, "Custom Range", tint = liveState.fontColor, modifier = Modifier.size(20.dp))
            }
        }

        // ── TODAY live card ───────────────────────────────────────
        TodayCard(
            screenTimeSecs = liveState.accumulatedSeconds,
            violations = todayViolations
        )

        Spacer(Modifier.height(16.dp))

        // ── Range Selectors ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RangeButton(
                label = "Weekly",
                range = weekRangeLabel,
                selected = selectedPeriod == HistoryPeriod.Week,
                onClick = { selectedPeriod = HistoryPeriod.Week },
                modifier = Modifier.weight(1f)
            )
            RangeButton(
                label = "Monthly",
                range = monthRangeLabel,
                selected = selectedPeriod == HistoryPeriod.Month,
                onClick = { selectedPeriod = HistoryPeriod.Month },
                modifier = Modifier.weight(1f)
            )
            RangeButton(
                label = "Yearly",
                range = yearRangeLabel,
                selected = selectedPeriod == HistoryPeriod.Year,
                onClick = { selectedPeriod = HistoryPeriod.Year },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Chart Section ─────────────────────────────────────────
        if (rangeData.isNotEmpty()) {
            UsageChart(
                records = rangeData,
                accentColor = liveState.fontColor
            )
        } else {
            EmptyState("No data for this range.")
        }

        Spacer(Modifier.height(16.dp))

        // ── Detailed Stats List ───────────────────────────────────
        Text(
            "Detailed Breakdown",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sortedRecords = rangeData.filter { it.screenTimeSecs > 0 || it.distanceViolations > 0 }.reversed()
            if (sortedRecords.isEmpty()) {
                EmptyState("No non-zero activity records.")
            } else {
                sortedRecords.forEach { record ->
                    HistoryCard(
                        label = record.displayLabel,
                        screenTimeSecs = record.screenTimeSecs,
                        violations = record.distanceViolations
                    )
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
    }

    if (showCustomRangePicker) {
        DateRangeSelectionDialog(
            initialStartDate = customStartDate ?: Date(),
            initialEndDate = customEndDate ?: Date(),
            onDismiss = { showCustomRangePicker = false },
            onRangeSelected = { start, end ->
                customStartDate = start
                customEndDate = end
                selectedPeriod = HistoryPeriod.Custom
                showCustomRangePicker = false
            }
        )
    }
}

@Composable
fun RangeButton(
    label: String,
    range: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentCyan.copy(alpha = 0.2f) else GlassBg,
        border = BorderStroke(1.dp, if (selected) AccentCyan.copy(alpha = 0.5f) else GlassBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = if (selected) AccentCyan else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(range, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
fun UsageChart(
    records: List<AnalyticsManager.DayRecord>,
    accentColor: Color
) {
    val maxTime = (records.maxOfOrNull { it.screenTimeSecs } ?: 1L).coerceAtLeast(1L)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Activity", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Max: ${maxTime / 60}m",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val displayRecords = records.takeLast(20) // Consistent count
                displayRecords.forEach { record ->
                    val barHeight = (record.screenTimeSecs.toFloat() / maxTime.toFloat()).coerceIn(0.05f, 1f)
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(accentColor, accentColor.copy(alpha = 0.3f))
                                    )
                                )
                        )
                    }
                }
            }
        }
        
        // Bottom labels (first and last date in view)
        val displayRecords = records.takeLast(20)
        if (displayRecords.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(displayRecords.first().displayLabel, color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                Text(displayRecords.last().displayLabel, color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun DateRangeSelectionDialog(
    initialStartDate: Date,
    initialEndDate: Date,
    onDismiss: () -> Unit,
    onRangeSelected: (Date, Date) -> Unit
) {
    var startDay by remember { mutableStateOf(initialStartDate.dayOf()) }
    var startMonth by remember { mutableStateOf(initialStartDate.month()) }
    var startYear by remember { mutableStateOf(initialStartDate.year()) }

    var endDay by remember { mutableStateOf(initialEndDate.dayOf()) }
    var endMonth by remember { mutableStateOf(initialEndDate.month()) }
    var endYear by remember { mutableStateOf(initialEndDate.year()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date Range", color = Color.White) },
        containerColor = Color(0xFF1A1F2B),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Start Date", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                DateSelector(startDay, startMonth, startYear) { d, m, y ->
                    startDay = d; startMonth = m; startYear = y
                }
                
                Divider(color = Color.White.copy(alpha = 0.1f))
                
                Text("End Date", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                DateSelector(endDay, endMonth, endYear) { d, m, y ->
                    endDay = d; endMonth = m; endYear = y
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = createDate(startDay, startMonth, startYear)
                val end = createDate(endDay, endMonth, endYear)
                onRangeSelected(start, end)
            }) {
                Text("Apply", color = AccentCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

@Composable
fun DateSelector(day: Int, month: Int, year: Int, onUpdate: (Int, Int, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberPicker(value = day, range = 1..31, label = "DD", modifier = Modifier.weight(1f)) { onUpdate(it, month, year) }
        NumberPicker(value = month + 1, range = 1..12, label = "MM", modifier = Modifier.weight(1f)) { onUpdate(day, it - 1, year) }
        NumberPicker(value = year, range = 2024..2030, label = "YYYY", modifier = Modifier.weight(1.5f)) { onUpdate(day, month, it) }
    }
}

@Composable
fun NumberPicker(value: Int, range: IntRange, label: String, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, modifier = Modifier.size(24.dp)) {
                    Text("-", color = Color.White, fontSize = 18.sp)
                }
                Text(value.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, modifier = Modifier.size(24.dp)) {
                    Text("+", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

// Helpers for Date manipulation
fun Date.dayOf(): Int = Calendar.getInstance().apply { time = this@dayOf }.get(Calendar.DAY_OF_MONTH)
fun Date.month(): Int = Calendar.getInstance().apply { time = this@month }.get(Calendar.MONTH)
fun Date.year(): Int = Calendar.getInstance().apply { time = this@year }.get(Calendar.YEAR)
fun createDate(day: Int, month: Int, year: Int): Date = Calendar.getInstance().apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, day)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

@Composable
private fun TodayCard(screenTimeSecs: Long, violations: Int) {
    val h = (screenTimeSecs / 3600).toInt()
    val m = ((screenTimeSecs % 3600) / 60).toInt()
    val s = (screenTimeSecs % 60).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.15f), AccentPurple.copy(alpha = 0.15f)))
            )
            .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Text("Today (Live)", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlock(
                    icon = { Icon(Icons.Filled.AccessTime, null, tint = AccentCyan, modifier = Modifier.size(13.dp)) },
                    value = if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s),
                    label = "Screen Time"
                )
                StatBlock(
                    icon = { Icon(Icons.Filled.Visibility, null, tint = AccentPink, modifier = Modifier.size(13.dp)) },
                    value = violations.toString(),
                    label = "Too Close"
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(label: String, screenTimeSecs: Long, violations: Int) {
    val h = (screenTimeSecs / 3600).toInt()
    val m = ((screenTimeSecs % 3600) / 60).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlock(
                    icon = { Icon(Icons.Filled.AccessTime, null, tint = AccentCyan, modifier = Modifier.size(12.dp)) },
                    value = if (h > 0) "${h}h ${m}m" else "${m}m",
                    label = "Time"
                )
                StatBlock(
                    icon = { Icon(Icons.Filled.Visibility, null, tint = AccentPink, modifier = Modifier.size(12.dp)) },
                    value = violations.toString(),
                    label = "Close"
                )
            }
        }
    }
}

@Composable
private fun StatBlock(
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            icon()
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
