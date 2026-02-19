package com.example.helloworld

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages persistent history of daily screen time and distance violations.
 * Each day is stored in SharedPreferences as two values:
 *   "screentime_YYYY-MM-DD" -> accumulated seconds (Long)
 *   "violations_YYYY-MM-DD" -> distance violation count (Int)
 */
class AnalyticsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("analytics_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ── Data class ──────────────────────────────────────────────────

    data class DayRecord(
        val date: String,          // "yyyy-MM-dd"
        val screenTimeSecs: Long,
        val distanceViolations: Int
    ) {
        val screenTimeHours: Int get() = (screenTimeSecs / 3600).toInt()
        val screenTimeMinutes: Int get() = ((screenTimeSecs % 3600) / 60).toInt()
        val displayLabel: String get() {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val cal = Calendar.getInstance()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(sdf.parse(date)!!)
                }
            } catch (_: Exception) { date }
        }
    }

    data class PeriodAggregate(
        val label: String,
        val screenTimeSecs: Long,
        val distanceViolations: Int
    ) {
        val screenTimeHours: Int get() = (screenTimeSecs / 3600).toInt()
        val screenTimeMinutes: Int get() = ((screenTimeSecs % 3600) / 60).toInt()
    }

    // ── Write ───────────────────────────────────────────────────────

    /**
     * Archives a day's data. Call this from MidnightResetReceiver BEFORE resetting counters.
     * Adds to any existing values for the date (safe to call multiple times).
     */
    fun recordDaySnapshot(date: String, screenTimeSecs: Long, violations: Int) {
        val existingTime = prefs.getLong("screentime_$date", 0L)
        val existingViolations = prefs.getInt("violations_$date", 0)
        prefs.edit()
            .putLong("screentime_$date", existingTime + screenTimeSecs)
            .putInt("violations_$date", existingViolations + violations)
            .apply()
    }

    // ── Read ────────────────────────────────────────────────────────

    fun getRecord(date: String): DayRecord {
        return DayRecord(
            date = date,
            screenTimeSecs = prefs.getLong("screentime_$date", 0L),
            distanceViolations = prefs.getInt("violations_$date", 0)
        )
    }

    fun getTodayDate(): String = dateFormat.format(Date())

    /** Returns records for the last [days] days (not including today, which is live). */
    fun getLastDays(days: Int): List<DayRecord> {
        val cal = Calendar.getInstance()
        return (1..days).map { i ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            getRecord(dateFormat.format(cal.time))
        }
    }

    /** Returns weekly aggregated records for the last [weeks] weeks. */
    fun getLastWeeks(weeks: Int): List<PeriodAggregate> {
        val cal = Calendar.getInstance()
        return (0 until weeks).map { weekIndex ->
            // Week label
            cal.time = Date()
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.add(Calendar.WEEK_OF_YEAR, -weekIndex)
            val label = if (weekIndex == 0) "This Week"
                        else "Week of ${SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)}"

            var totalSecs = 0L
            var totalViolations = 0
            for (day in 0..6) {
                val dayDate = dateFormat.format(cal.time)
                val record = getRecord(dayDate)
                totalSecs += record.screenTimeSecs
                totalViolations += record.distanceViolations
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            PeriodAggregate(label, totalSecs, totalViolations)
        }
    }

    /** Returns monthly aggregated records for the last [months] months. */
    fun getLastMonths(months: Int): List<PeriodAggregate> {
        val cal = Calendar.getInstance()
        return (0 until months).map { monthIndex ->
            cal.time = Date()
            cal.add(Calendar.MONTH, -monthIndex)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            var totalSecs = 0L
            var totalViolations = 0
            for (day in 0 until daysInMonth) {
                val dayDate = dateFormat.format(cal.time)
                val record = getRecord(dayDate)
                totalSecs += record.screenTimeSecs
                totalViolations += record.distanceViolations
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            PeriodAggregate(label, totalSecs, totalViolations)
        }
    }

    /** Returns yearly aggregated records for the last [years] years. */
    fun getLastYears(years: Int): List<PeriodAggregate> {
        val cal = Calendar.getInstance()
        return (0 until years).map { yearIndex ->
            cal.time = Date()
            cal.add(Calendar.YEAR, -yearIndex)
            cal.set(Calendar.DAY_OF_YEAR, 1)
            val label = SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.time)
            val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)

            var totalSecs = 0L
            var totalViolations = 0
            for (day in 0 until daysInYear) {
                val dayDate = dateFormat.format(cal.time)
                val record = getRecord(dayDate)
                totalSecs += record.screenTimeSecs
                totalViolations += record.distanceViolations
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            PeriodAggregate(label, totalSecs, totalViolations)
        }
    }
}
