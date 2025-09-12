package com.example.tinnitusaiish.model
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeeklyReport(
    val tinnitusLevels: List<Int?>,
    val anxietyLevels: List<Int?>,
    val relaxationDays: Int,
    val soundTherapyDays: Int,
    val averageTinnitus: Double,
    val averageAnxiety: Double
)

fun generateReport(checkIns: List<CheckIn>): WeeklyReport {
    if (checkIns.isEmpty()) {
        return WeeklyReport(
            tinnitusLevels = List(7) { null },
            anxietyLevels = List(7) { null },
            relaxationDays = 0,
            soundTherapyDays = 0,
            averageTinnitus = 0.0,
            averageAnxiety = 0.0
        )
    }

    val sorted = checkIns.sortedBy { it.date }

    val tinnitusLevels = mutableListOf<Int?>()
    val anxietyLevels = mutableListOf<Int?>()
    var relaxationDays = 0
    var soundTherapyDays = 0

    val seenDates = mutableSetOf<String>()

    for (entry in sorted) {
        if (seenDates.contains(entry.date)) continue
        seenDates.add(entry.date)

        tinnitusLevels.add(entry.tinnitusLevel)
        anxietyLevels.add(entry.anxietyLevel)

        if (entry.relaxationDone == "Yes") relaxationDays++
        if (entry.soundTherapyDone == "Yes") soundTherapyDays++
    }

    val averageTinnitus = tinnitusLevels.filterNotNull().average()
    val averageAnxiety = anxietyLevels.filterNotNull().average()

    return WeeklyReport(
        tinnitusLevels = tinnitusLevels,
        anxietyLevels = anxietyLevels,
        relaxationDays = relaxationDays,
        soundTherapyDays = soundTherapyDays,
        averageTinnitus = averageTinnitus,
        averageAnxiety = averageAnxiety
    )
}

fun getStartDate(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -6)
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
}