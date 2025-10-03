package com.aiish.tinnitus.ui.user

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aiish.tinnitus.captureLineChartAsBitmap
import com.aiish.tinnitus.createLineChart
import com.aiish.tinnitus.data.ReportExporter
import com.aiish.tinnitus.data.ReportRepository
import com.aiish.tinnitus.model.WeeklyReport
import com.aiish.tinnitus.model.generateReport
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
    val email = prefs.getString("logged_in_email", "") ?: ""

    var reportRange by remember { mutableStateOf("weekly") }
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var report by remember { mutableStateOf<WeeklyReport?>(null) }
    var weeklySummaries by remember { mutableStateOf<List<WeeklyReport>>(emptyList()) }
    var tinnitusBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var anxietyBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var userNote by remember { mutableStateOf("") }
    val patientName = prefs.getString("logged_in_name", "Test User") ?: "Test User"

    var showSuggestionPopup by remember { mutableStateOf(false) }
    var suggestionMessage by remember { mutableStateOf("") }

    LaunchedEffect(reportRange) {
        scope.launch {
            isLoading = true
            statusMessage = null
            try {
                val repo = ReportRepository()
                val all = repo.fetchCheckIns(email)
                val filtered = repo.filterByRange(all, reportRange, todayIso)

                val finalCheckIns = if (reportRange == "weekly") repo.padWeeklyData(filtered, email) else filtered
                val sorted = finalCheckIns.sortedBy { it.date }

                report = generateReport(sorted)
                weeklySummaries = if (reportRange == "since_signup") repo.generateWeeklyChunks(sorted) else emptyList()

                // ---- Build padded daily series for graphs ----
                val (startIso, endIso) = when (reportRange) {
                    "weekly" -> {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time) to todayIso
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
                        val start = sorted.firstOrNull()?.date ?: todayIso
                        start to todayIso
                    }
                    else -> todayIso to todayIso
                }

                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val byDate = sorted.associateBy { it.date }
                val tSeries: List<Int?>
                val aSeries: List<Int?>

                if (reportRange == "since_signup") {
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


                val tChart = createLineChart(
                    context,
                    label = "Tinnitus Trend",
                    values = tSeries,
                    rangeType = reportRange,
                    startDateIso = startIso,
                    endDateIso = endIso
                )
                val aChart = createLineChart(
                    context,
                    label = "Anxiety Trend",
                    values = aSeries,
                    rangeType = reportRange,
                    startDateIso = startIso,
                    endDateIso = endIso
                )

                tinnitusBitmap = captureLineChartAsBitmap(tChart)
                anxietyBitmap = captureLineChartAsBitmap(aChart)

            } catch (e: Exception) {
                e.printStackTrace()
                statusMessage = "❌ Failed to load report: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ReportHeader(reportRange = reportRange, onRangeChange = { reportRange = it })
        ReportStatusView(isLoading, statusMessage)

        if (!isLoading && report != null) {
            ReportContent(
                report = report!!,
                reportRange = reportRange,
                endDate = todayIso,
                tinnitusBitmap = tinnitusBitmap,
                anxietyBitmap = anxietyBitmap,
                weeklySummaries = weeklySummaries
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = userNote,
            onValueChange = { userNote = it },
            label = { Text("Optional Notes for Report") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )

        ReportExportButton(
            isLoading = isLoading,
            reportRange = reportRange,
            report = report,
            weeklySummaries = weeklySummaries,
            patientName = patientName,
            userNote = userNote,
            onStatus = { msg ->
                statusMessage = msg
                if (msg.startsWith("✅")) {
                    report?.let { r ->
                        if (reportRange == "weekly") {
                            val avg = (r.averageTinnitus + r.averageAnxiety) / 2.0
                            suggestionMessage = if (avg >= 7) {
                                "Your average weekly scores are high. Contact your audiologist for expert advice."
                            } else if (avg <= 5) {
                                "You're making progress. Continue practicing."
                            } else ""
                            showSuggestionPopup = suggestionMessage.isNotEmpty()
                        }
                    }
                }
            },
            onLoading = { isLoading = it }
        )
    }

    if (showSuggestionPopup) {
        AlertDialog(
            onDismissRequest = { showSuggestionPopup = false },
            title = { Text("Technique Suggestion") },
            text = { Text(suggestionMessage) },
            confirmButton = { Button(onClick = { showSuggestionPopup = false }) { Text("OK") } }
        )
    }
}

@Composable
fun ReportHeader(reportRange: String, onRangeChange: (String) -> Unit) {
    Text("Progress Report", style = MaterialTheme.typography.headlineSmall)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onRangeChange("weekly") }, enabled = reportRange != "weekly") { Text("Weekly") }
        Button(onClick = { onRangeChange("monthly") }, enabled = reportRange != "monthly") { Text("Monthly") }
        Button(onClick = { onRangeChange("since_signup") }, enabled = reportRange != "since_signup") { Text("Since Signup") }
    }
}

@Composable
fun ReportStatusView(isLoading: Boolean, statusMessage: String?) {
    if (isLoading) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading report...")
        }
    }
    statusMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun ReportContent(
    report: WeeklyReport,
    reportRange: String,
    endDate: String,
    tinnitusBitmap: Bitmap?,
    anxietyBitmap: Bitmap?,
    weeklySummaries: List<WeeklyReport>
) {
    ReportRangeLabel(reportRange, endDate)
    ReportCharts(tinnitusBitmap, anxietyBitmap)

    val weeks = when (reportRange) {
        "since_signup" -> weeklySummaries.size
        "monthly" -> 4
        else -> 1
    }

    ReportSection("Total Weeks", weeks.toString())
    ReportSection("Overall Avg Tinnitus", String.format("%.2f", report.averageTinnitus))
    ReportSection("Overall Avg Anxiety", String.format("%.2f", report.averageAnxiety))

    val totalDays = if (reportRange == "since_signup") weeks * 7 else report.tinnitusLevels.size
    val relaxRatio = if (totalDays > 0) (report.relaxationDays * 100 / totalDays) else 0
    val soundRatio = if (totalDays > 0) (report.soundTherapyDays * 100 / totalDays) else 0

    ReportSection("Relaxation Used", "$relaxRatio% of days")
    ReportSection("Sound Therapy Used", "$soundRatio% of days")
}

@Composable
fun ReportRangeLabel(range: String, endDate: String) {
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val text = when (range) {
        "weekly" -> {
            val start = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
            "Date Range: ${df.format(start.time)} to $endDate"
        }
        "monthly" -> {
            val start = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val end = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)) }
            "Date Range: ${df.format(start.time)} to ${df.format(end.time)}"
        }
        "since_signup" -> "Full history since signup"
        else -> ""
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ReportCharts(tinnitusBitmap: Bitmap?, anxietyBitmap: Bitmap?) {
    tinnitusBitmap?.let {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap = it.asImageBitmap(), contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
    }
    anxietyBitmap?.let {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap = it.asImageBitmap(), contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable fun ReportSection(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ReportExportButton(
    isLoading: Boolean,
    reportRange: String,
    report: WeeklyReport?,
    weeklySummaries: List<WeeklyReport>,
    patientName: String,
    userNote: String,
    onStatus: (String) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            report?.let { r ->
                scope.launch {
                    onLoading(true); onStatus("")
                    try {
                        val db = FirebaseFirestore.getInstance()   // ✅ FIX

                        // ✅ fetch all admins from Firestore
                        val snapshot = db.collection("admins").get().await()
                        val adminEmails = snapshot.documents.mapNotNull { it.getString("email") }

                        // ✅ Debug: log and show which admins will receive the email
                        Log.d("ReportExportButton", "Sending report to admins: $adminEmails")
                        onStatus("📧 Sending to: ${adminEmails.joinToString(", ")}")

                        val exporter = ReportExporter()

                        // send report to every admin
                        adminEmails.forEach { adminEmail ->
                            val result = exporter.exportAndSendReport(
                                context = context,
                                report = r,
                                tinnitusData = r.tinnitusLevels,
                                anxietyData = r.anxietyLevels,
                                patientName = patientName,
                                userNote = userNote,
                                doctorEmail = adminEmail,   // 👈 dynamic now
                                reportRange = reportRange
                            )
                            result.fold(
                                onSuccess = { onStatus("✅ Sent to $adminEmail") },
                                onFailure = { onStatus("❌ Failed for $adminEmail: ${it.localizedMessage}") }
                            )
                        }
                    } finally { onLoading(false) }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) { Text("Send to Doctor") }
}


