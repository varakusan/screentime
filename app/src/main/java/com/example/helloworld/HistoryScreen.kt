package com.example.helloworld

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
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

private enum class HistoryPeriod(val label: String) {
    Day("Day"), Week("Week"), Month("Month"), Year("Year")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val analytics = remember { AnalyticsManager(context) }
    val screenTimeManager = remember { ScreenTimeManager(context) }

    var selectedPeriod by remember { mutableStateOf(HistoryPeriod.Day) }

    // Re-derive data whenever period changes
    val liveState by SettingsState.state.collectAsState()
    val todayViolations = remember(liveState) { screenTimeManager.getDistanceViolationCount() }

    // Compute list items
    val dayRecords = remember(selectedPeriod) { analytics.getLastDays(30) }
    val weekAggregates = remember(selectedPeriod) { analytics.getLastWeeks(12) }
    val monthAggregates = remember(selectedPeriod) { analytics.getLastMonths(12) }
    val yearAggregates = remember(selectedPeriod) { analytics.getLastYears(5) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Header with Back Button ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Usage History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // ── TODAY live card ───────────────────────────────────────
        TodayCard(
            screenTimeSecs = liveState.accumulatedSeconds,
            violations = todayViolations
        )

        Spacer(Modifier.height(12.dp))

        // ── Period selector chips ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HistoryPeriod.entries.forEach { period ->
                val selected = period == selectedPeriod
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) AccentCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(50)
                        )
                ) {
                    FilterChip(
                        selected = selected,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.25f),
                            selectedLabelColor = AccentCyan,
                            containerColor = Color.White.copy(alpha = 0.07f),
                            labelColor = Color.White.copy(alpha = 0.6f)
                        ),
                        border = null
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── History list ──────────────────────────────────────────
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 300.dp) // Constrain height for embedding
        ) {
            val itemsToShow = when (selectedPeriod) {
                HistoryPeriod.Day -> dayRecords.filter { it.screenTimeSecs > 0 || it.distanceViolations > 0 }
                    .map { it.displayLabel to (it.screenTimeSecs to it.distanceViolations) }
                HistoryPeriod.Week -> weekAggregates.filter { it.screenTimeSecs > 0 || it.distanceViolations > 0 }
                    .map { it.label to (it.screenTimeSecs to it.distanceViolations) }
                HistoryPeriod.Month -> monthAggregates.filter { it.screenTimeSecs > 0 || it.distanceViolations > 0 }
                    .map { it.label to (it.screenTimeSecs to it.distanceViolations) }
                HistoryPeriod.Year -> yearAggregates.filter { it.screenTimeSecs > 0 || it.distanceViolations > 0 }
                    .map { it.label to (it.screenTimeSecs to it.distanceViolations) }
            }

            if (itemsToShow.isEmpty()) {
                item { EmptyState("No history yet.\nData archived each midnight.") }
            } else {
                items(itemsToShow) { (label, data) ->
                    HistoryCard(
                        label = label,
                        screenTimeSecs = data.first,
                        violations = data.second
                    )
                }
            }
        }
    }
}

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
