package com.example.tinnitusaiish.ui

import android.graphics.Bitmap
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.example.tinnitusaiish.captureLineChartAsBitmap
import com.example.tinnitusaiish.createLineChart
import com.example.tinnitusaiish.data.ReportExporter
import com.example.tinnitusaiish.data.ReportRepository
import com.example.tinnitusaiish.model.WeeklyReport
import com.example.tinnitusaiish.model.generateReport
import kotlinx.coroutines.launch
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
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var report by remember { mutableStateOf<WeeklyReport?>(null) }
    var weeklySummaries by remember { mutableStateOf<List<WeeklyReport>>(emptyList()) }
    var tinnitusBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var anxietyBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var userNote by remember { mutableStateOf("") }
    val patientName = prefs.getString("logged_in_name", "Test User") ?: "Test User"

    // 🔄 Load report when range changes
    LaunchedEffect(reportRange) {
        scope.launch {
            isLoading = true
            statusMessage = null
            try {
                val repo = ReportRepository()
                val allCheckIns = repo.fetchCheckIns(email)
                val filtered = repo.filterByRange(allCheckIns, reportRange, today)

                val finalCheckIns = if (reportRange == "weekly") {
                    repo.padWeeklyData(filtered, email)
                } else filtered

                val sortedCheckIns = finalCheckIns.sortedBy { it.date }
                report = generateReport(sortedCheckIns)
                weeklySummaries =
                    if (reportRange == "since_signup") repo.generateWeeklyChunks(sortedCheckIns)
                    else emptyList()

                val tinnitusData = if (reportRange == "since_signup")
                    weeklySummaries.map { it.averageTinnitus.toInt() }
                else report?.tinnitusLevels ?: emptyList()

                val anxietyData = if (reportRange == "since_signup")
                    weeklySummaries.map { it.averageAnxiety.toInt() }
                else report?.anxietyLevels ?: emptyList()

                val tinnitusChart = createLineChart(context, "Tinnitus Trend", tinnitusData)
                val anxietyChart = createLineChart(context, "Anxiety Trend", anxietyData)

                tinnitusBitmap = captureLineChartAsBitmap(tinnitusChart)
                anxietyBitmap = captureLineChartAsBitmap(anxietyChart)

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
                endDate = today,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        ReportExportButton(
            isLoading = isLoading,
            reportRange = reportRange,
            report = report,
            weeklySummaries = weeklySummaries,
            patientName = patientName,
            userNote = userNote,
            onStatus = { statusMessage = it },
            onLoading = { isLoading = it }
        )
    }
}

@Composable
fun ReportHeader(reportRange: String, onRangeChange: (String) -> Unit) {
    Text("Progress Report", style = MaterialTheme.typography.headlineSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading report...")
        }
    }

    statusMessage?.let { msg ->
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
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

    val weeks = if (reportRange == "since_signup") weeklySummaries.size else 1
    ReportSection("Total Weeks", weeks.toString())
    ReportSection("Overall Avg Tinnitus", String.format("%.2f", report.averageTinnitus))
    ReportSection("Overall Avg Anxiety", String.format("%.2f", report.averageAnxiety))

    val totalDays = if (reportRange == "since_signup") weeks * 7 else report.tinnitusLevels.size
    val relaxRatio = if (totalDays > 0) (report.relaxationDays * 100 / totalDays) else 0
    val soundRatio = if (totalDays > 0) (report.soundTherapyDays * 100 / totalDays) else 0

    ReportSection("Relaxation Used", "$relaxRatio% of days")
    ReportSection("Sound Therapy Used", "$soundRatio% of days")

    if (reportRange == "since_signup") {
        Spacer(Modifier.height(16.dp))
        Text("Week-by-week summary:", style = MaterialTheme.typography.titleMedium)
        weeklySummaries.forEachIndexed { i, week ->
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportSection(
                "Week ${i + 1}",
                "Tinnitus: %.1f, Anxiety: %.1f".format(
                    week.averageTinnitus,
                    week.averageAnxiety
                )
            )
        }
    }
}

@Composable
fun ReportRangeLabel(reportRange: String, endDate: String) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val rangeLabel = when (reportRange) {
        "weekly", "monthly" -> {
            val calendar = Calendar.getInstance()
            if (reportRange == "weekly") calendar.add(Calendar.DAY_OF_YEAR, -6)
            if (reportRange == "monthly") calendar.add(Calendar.DAY_OF_YEAR, -29)
            val start = dateFormat.format(calendar.time)
            "Date Range: $start to $endDate"
        }
        "since_signup" -> "Full history since signup"
        else -> ""
    }
    Text(rangeLabel, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ReportCharts(tinnitusBitmap: Bitmap?, anxietyBitmap: Bitmap?) {
    tinnitusBitmap?.let {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(bitmap = it.asImageBitmap(), contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
    }
    anxietyBitmap?.let {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(bitmap = it.asImageBitmap(), contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ReportSection(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
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
            report?.let { reportData ->
                scope.launch {
                    onLoading(true)
                    onStatus("")
                    try {
                        val exporter = ReportExporter()

                        val tinnitusData = if (reportRange == "since_signup")
                            weeklySummaries.map { it.averageTinnitus.toInt() }
                        else reportData.tinnitusLevels

                        val anxietyData = if (reportRange == "since_signup")
                            weeklySummaries.map { it.averageAnxiety.toInt() }
                        else reportData.anxietyLevels

                        val result = exporter.exportAndSendReport(
                            context = context,
                            report = reportData,
                            tinnitusData = tinnitusData,
                            anxietyData = anxietyData,
                            patientName = patientName,
                            userNote = userNote,
                            doctorEmail = "tahamurtaza21@outlook.com"
                        )

                        result.fold(
                            onSuccess = { onStatus("✅ $it") },
                            onFailure = { onStatus("❌ Failed: ${it.localizedMessage}") }
                        )
                    } finally {
                        onLoading(false)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        Text("Send to Doctor")
    }
}
