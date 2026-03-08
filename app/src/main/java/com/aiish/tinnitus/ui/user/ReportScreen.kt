package com.aiish.tinnitus.ui.user

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aiish.tinnitus.captureLineChartAsBitmap
import com.aiish.tinnitus.createLineChart
import com.aiish.tinnitus.data.ReportRepository
import com.aiish.tinnitus.model.WeeklyReport
import com.aiish.tinnitus.model.generateReport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ranges = listOf("weekly", "monthly", "since_signup")
private val rangeTabs = listOf("Weekly", "Monthly", "Since Signup")

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
                statusMessage = "Failed to load report: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header row with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Progress Report",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Tab row range selector
        val selectedTab = ranges.indexOf(reportRange).coerceAtLeast(0)
        TabRow(selectedTabIndex = selectedTab) {
            rangeTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { reportRange = ranges[index] },
                    text = { Text(title) }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
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
        }
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
fun ReportStatusView(isLoading: Boolean, statusMessage: String?) {
    if (isLoading) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading report...", style = MaterialTheme.typography.bodyMedium)
        }
    }
    statusMessage?.let {
        Text(
            text = it,
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

    Spacer(Modifier.height(16.dp))
    ReportCharts(tinnitusBitmap, anxietyBitmap)

    Spacer(Modifier.height(16.dp))

    val weeks = when (reportRange) {
        "since_signup" -> weeklySummaries.size
        "monthly" -> 4
        else -> 1
    }
    val totalDays = if (reportRange == "since_signup") weeks * 7 else report.tinnitusLevels.size
    val relaxRatio = if (totalDays > 0) (report.relaxationDays * 100 / totalDays) else 0
    val soundRatio = if (totalDays > 0) (report.soundTherapyDays * 100 / totalDays) else 0

    // Averages row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Avg Tinnitus",
            value = String.format("%.1f", report.averageTinnitus),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Avg Anxiety",
            value = String.format("%.1f", report.averageAnxiety),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(12.dp))

    // Therapy usage row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Relaxation",
            value = "$relaxRatio%",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Sound Therapy",
            value = "$soundRatio%",
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(12.dp))

    StatCard(
        label = "Total Weeks",
        value = weeks.toString(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReportRangeLabel(range: String, endDate: String) {
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val text = when (range) {
        "weekly" -> {
            val start = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
            "${df.format(start.time)}  →  $endDate"
        }
        "monthly" -> {
            val start = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val end = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            "${df.format(start.time)}  →  ${df.format(end.time)}"
        }
        "since_signup" -> "Full history since signup"
        else -> ""
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ReportCharts(tinnitusBitmap: Bitmap?, anxietyBitmap: Bitmap?) {
    tinnitusBitmap?.let { bitmap ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Tinnitus Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Tinnitus trend chart",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    anxietyBitmap?.let { bitmap ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Anxiety Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Anxiety trend chart",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
