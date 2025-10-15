package com.aiish.tinnitus.ui.admin

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiish.tinnitus.data.ReportRepository
import com.aiish.tinnitus.model.generateReport
import com.aiish.tinnitus.util.generatePdfWithChartData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportItem(
    val name: String,
    val url: String?,
    val isGenerating: Boolean,
    val reportRange: String
)

@Composable
fun AdminUserReportsScreen(uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reports by remember { mutableStateOf<List<ReportItem>>(emptyList()) }
    var currentAdminEmail by remember { mutableStateOf<String?>(null) }

    // ✅ Get logged-in admin email
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        currentAdminEmail = prefs.getString("logged_in_email", null)
    }

    // ✅ Generate all three reports on screen load
    LaunchedEffect(uid) {
        // Initialize with loading states
        reports = listOf(
            ReportItem("Weekly Report", null, true, "weekly"),
            ReportItem("Monthly Report", null, true, "monthly"),
            ReportItem("Full History Report", null, true, "since_signup")
        )

        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val userDoc = db.collection("users").document(uid).get().await()
                val patientName = userDoc.getString("name") ?: "Patient"
                val patientEmail = userDoc.getString("email") ?: ""

                val repository = ReportRepository()
                val allCheckIns = repository.fetchCheckIns(patientEmail)

                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = df.format(Date())
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                val reportTypes = listOf(
                    Triple("weekly", "Weekly", "Weekly Report"),
                    Triple("monthly", "Monthly", "Monthly Report"),
                    Triple("since_signup", "Full", "Full History Report")
                )

                val generatedReports = mutableListOf<ReportItem>()

                reportTypes.forEach { (range, label, displayName) ->
                    try {
                        // ✅ EXACTLY match ReportScreen.kt logic
                        val filtered = repository.filterByRange(allCheckIns, range, today)

                        // ✅ PAD weekly data just like ReportScreen does!
                        val finalCheckIns = if (range == "weekly") {
                            repository.padWeeklyData(filtered, patientEmail)
                        } else {
                            filtered
                        }

                        val sorted = finalCheckIns.sortedBy { it.date }
                        val reportData = generateReport(sorted)

                        // ✅ Build the SAME padded series that ReportScreen uses for charts
                        val (startIso, endIso) = when (range) {
                            "weekly" -> {
                                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time) to today
                            }
                            "monthly" -> {
                                val cStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                                val cEnd = Calendar.getInstance().apply {
                                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                                }
                                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                df.format(cStart.time) to df.format(cEnd.time)
                            }
                            "since_signup" -> {
                                val start = sorted.firstOrNull()?.date ?: today
                                start to today
                            }
                            else -> today to today
                        }

                        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val byDate = sorted.associateBy { it.date }

                        // Build padded series with nulls for missing days
                        val tSeries: List<Int?>
                        val aSeries: List<Int?>

                        if (range == "since_signup") {
                            // group by ISO week and take averages
                            val grouped = sorted.groupBy { ci ->
                                val cal = Calendar.getInstance().apply { time = df.parse(ci.date)!! }
                                "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.WEEK_OF_YEAR)}"
                            }
                            tSeries = grouped.values.map { week ->
                                week.mapNotNull { it.tinnitusLevel }.average().takeIf { !it.isNaN() }?.toInt()
                            }
                            aSeries = grouped.values.map { week ->
                                week.mapNotNull { it.anxietyLevel }.average().takeIf { !it.isNaN() }?.toInt()
                            }
                        } else {
                            // For weekly and monthly: pad all days in range
                            val tmpT = mutableListOf<Int?>()
                            val tmpA = mutableListOf<Int?>()
                            val c = Calendar.getInstance().apply { time = df.parse(startIso)!! }
                            val end = Calendar.getInstance().apply { time = df.parse(endIso)!! }
                            while (!c.after(end)) {
                                val d = df.format(c.time)
                                val ci = byDate[d]
                                tmpT.add(ci?.tinnitusLevel)
                                tmpA.add(ci?.anxietyLevel)
                                c.add(Calendar.DAY_OF_YEAR, 1)
                            }
                            tSeries = tmpT
                            aSeries = tmpA
                        }

                        // ✅ Generate PDF with padded chart data
                        val pdfFile = generatePdfWithChartData(
                            context = context,
                            report = reportData,
                            tinnitusData = tSeries,
                            anxietyData = aSeries,
                            patientName = patientName,
                            userNote = "Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                            reportRange = range,
                            startDateIso = startIso,
                            endDateIso = endIso
                        )

                        // Upload to Firebase Storage
                        val fileName = "Tinnitus_${label}_${timestamp}.pdf"
                        val storageRef = FirebaseStorage.getInstance().reference
                            .child("reports")
                            .child(uid)
                            .child(fileName)

                        FileInputStream(pdfFile).use { stream ->
                            storageRef.putStream(stream).await()
                        }

                        val downloadUrl = storageRef.downloadUrl.await().toString()

                        generatedReports.add(
                            ReportItem(displayName, downloadUrl, false, range)
                        )

                        // Update UI progressively
                        reports = generatedReports + reportTypes.drop(generatedReports.size).map { (r, _, n) ->
                            ReportItem(n, null, true, r)
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        generatedReports.add(
                            ReportItem("$displayName (Failed)", null, false, range)
                        )
                    }
                }

                reports = generatedReports

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to generate reports: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                reports = reports.map { it.copy(isGenerating = false, name = "${it.name} (Failed)") }
            }
        }
    }

    // ✅ UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Patient Reports", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (report.isGenerating)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (report.isGenerating) {
                            // Show loading indicator
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    text = "Generating ${report.name}...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (report.url != null) {
                            // 📄 Clicking name opens PDF
                            Text(
                                text = "📄 ${report.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(report.url)
                                        )
                                        context.startActivity(intent)
                                    }
                            )

                            // ✉️ Send button
                            var isSending by remember { mutableStateOf(false) }

                            IconButton(
                                onClick = {
                                    val email = currentAdminEmail
                                    if (email.isNullOrBlank()) {
                                        Toast.makeText(context, "⚠️ No admin email found.", Toast.LENGTH_SHORT).show()
                                        return@IconButton
                                    }

                                    scope.launch {
                                        try {
                                            isSending = true
                                            Toast.makeText(context, "📤 Sending report...", Toast.LENGTH_SHORT).show()

                                            val db = FirebaseFirestore.getInstance()
                                            val userDoc = db.collection("users").document(uid).get().await()
                                            val patientName = userDoc.getString("name") ?: "Patient"

                                            // Send email via Cloud Function
                                            val data = hashMapOf(
                                                "email" to email,
                                                "fileUrl" to report.url,
                                                "fileName" to report.name,
                                                "patientName" to patientName,
                                                "reportRange" to report.reportRange
                                            )

                                            FirebaseFunctions.getInstance()
                                                .getHttpsCallable("sendDoctorReport")
                                                .call(data)
                                                .await()

                                            Toast.makeText(context, "✅ Report sent to $email", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "❌ ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            e.printStackTrace()
                                        } finally {
                                            isSending = false
                                        }
                                    }
                                },
                                enabled = !isSending
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send Report",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // Failed state
                            Text(
                                text = "❌ ${report.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}