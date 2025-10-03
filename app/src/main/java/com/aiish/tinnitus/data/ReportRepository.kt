package com.aiish.tinnitus.data

import com.aiish.tinnitus.model.CheckIn
import com.aiish.tinnitus.model.WeeklyReport
import com.aiish.tinnitus.model.generateReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun fetchCheckIns(email: String): List<CheckIn> {
        val snapshot = db.collection("checkins")
            .whereEqualTo("userEmail", email)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                CheckIn(
                    userEmail = doc.getString("userEmail") ?: "",
                    date = doc.getString("date") ?: "",
                    relaxationDone = doc.getString("relaxationDone") ?: "No",
                    relaxationDuration = doc.getString("relaxationDuration") ?: "<5 min",
                    soundTherapyDone = doc.getString("soundTherapyDone") ?: "No",
                    soundTherapyDuration = doc.getString("soundTherapyDuration") ?: "<10 min",
                    tinnitusLevel = (doc.getLong("tinnitusLevel") ?: 0).toInt(),
                    anxietyLevel = (doc.getLong("anxietyLevel") ?: 0).toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun filterByRange(allCheckIns: List<CheckIn>, range: String, endDate: String): List<CheckIn> {
        val calendar = Calendar.getInstance()
        if (range == "weekly") calendar.add(Calendar.DAY_OF_YEAR, -6)
        if (range == "monthly") calendar.add(Calendar.DAY_OF_YEAR, -29)

        val limitDate = calendar.time
        val endDateParsed = dateFormat.parse(endDate) ?: Date()

        return when (range) {
            "weekly", "monthly" -> allCheckIns.filter {
                val checkInDate = dateFormat.parse(it.date)
                checkInDate != null && !checkInDate.before(limitDate) && !checkInDate.after(endDateParsed)
            }
            "since_signup" -> allCheckIns
            else -> emptyList()
        }
    }

    fun padWeeklyData(filtered: List<CheckIn>, email: String): List<CheckIn> {
        val datesInRange = (0..6).map {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -6 + it)
            dateFormat.format(cal.time)
        }
        return datesInRange.map { date ->
            filtered.find { it.date == date } ?: CheckIn(
                userEmail = email,
                date = date,
                relaxationDone = "No",
                relaxationDuration = "<5 min",
                soundTherapyDone = "No",
                soundTherapyDuration = "<10 min",
                tinnitusLevel = null,
                anxietyLevel = null
            )
        }
    }

    fun generateWeeklyChunks(data: List<CheckIn>): List<WeeklyReport> {
        if (data.isEmpty()) return emptyList()
        val sorted = data.sortedBy { it.date }
        return sorted.chunked(7).map { generateReport(it) }
    }
}
